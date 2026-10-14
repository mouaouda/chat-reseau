package chatreseau.network;

import chatreseau.model.ChatMessage;
import chatreseau.model.PeerUser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gère la découverte automatique des utilisateurs sur le réseau local (UDP Multicast + Broadcast + Unicast)
 * la vérification d'unicité du nom/username, et l'échange de messages (UDP fiable avec ACK + TCP).
 * Conçu pour traverser le pare-feu Windows sur les réseaux LAN / Wi-Fi d'entreprise ou d'école.
 */
public class NetworkManager {

    public static final int DISCOVERY_PORT = 44446;
    public static final int LOCAL_INSTANCE_PORT_COUNT = 8; // 44446..44453
    public static final String MULTICAST_GROUP = "239.255.42.99";
    private static final long PEER_TIMEOUT_MS = 12000;
    private static final long HEARTBEAT_INTERVAL_MS = 1500;

    public interface NetworkListener {
        void onPeersUpdated(Collection<PeerUser> peers);
        void onMessageReceived(ChatMessage message);
        void onPeerJoined(PeerUser peer);
        void onPeerLeft(PeerUser peer);
        void onTypingReceived(String senderId, String senderName, String targetId);
        void onNameConflictDetected(String conflictingName);
    }

    private final String myId;
    private volatile String myUsername;
    private volatile boolean registered = false;
    private final long joinTimestamp;

    private ServerSocket tcpServerSocket;
    private int myTcpPort;

    private MulticastSocket multicastSocket;
    private DatagramSocket localSlotSocket;
    private InetAddress multicastGroupAddr;

    private final Map<String, PeerUser> peers = new ConcurrentHashMap<>();
    private final Set<String> takenNamesDuringCheck = ConcurrentHashMap.newKeySet();
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();
    private final Set<String> ackedMessageIds = ConcurrentHashMap.newKeySet();
    private final List<NetworkListener> listeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public NetworkManager() {
        this.myId = UUID.randomUUID().toString();
        this.joinTimestamp = System.currentTimeMillis();
    }

    public void addListener(NetworkListener listener) {
        listeners.add(listener);
    }

    public void removeListener(NetworkListener listener) {
        listeners.remove(listener);
    }

    public String getMyId() {
        return myId;
    }

    public String getMyUsername() {
        return myUsername;
    }

    public int getMyTcpPort() {
        return myTcpPort;
    }

    public int getMyUdpPort() {
        if (localSlotSocket != null && !localSlotSocket.isClosed()) {
            return localSlotSocket.getLocalPort();
        }
        return DISCOVERY_PORT;
    }

    public Collection<PeerUser> getPeers() {
        return new ArrayList<>(peers.values());
    }

    public PeerUser getPeer(String peerId) {
        return peers.get(peerId);
    }

    /**
     * Vérifie strictement qu'une adresse IP appartient au réseau local (LAN) ou à la machine locale.
     */
    public static boolean isLocalNetworkAddress(InetAddress addr) {
        if (addr == null) return false;
        return addr.isSiteLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress();
    }

    /**
     * Détecte la meilleure adresse IPv4 LAN (en privilégiant le vrai réseau Wi-Fi/Ethernet comme 10.x.x.x ou 192.168.x.x
     * par rapport aux cartes virtuelles Hyper-V / Default Switch / WSL).
     */
    public static String detectBestLanIp() {
        String fallback = "127.0.0.1";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface netIf : Collections.list(interfaces)) {
                    if (!netIf.isUp() || netIf.isLoopback()) continue;
                    String displayName = (netIf.getDisplayName() + " " + netIf.getName()).toLowerCase();
                    boolean isVirtualSwitch = displayName.contains("default switch")
                            || displayName.contains("wsl")
                            || displayName.contains("docker")
                            || displayName.contains("virtualbox")
                            || displayName.contains("vmware");

                    for (InetAddress addr : Collections.list(netIf.getInetAddresses())) {
                        if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                            String ip = addr.getHostAddress();
                            if (!isVirtualSwitch && (ip.startsWith("10.") || ip.startsWith("192.168."))) {
                                return ip;
                            }
                            if ("127.0.0.1".equals(fallback) || !isVirtualSwitch) {
                                fallback = ip;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /**
     * Démarre l'écoute réseau (UDP Multicast + Broadcast + Unicast + Serveur TCP).
     */
    public void startNetworkServices() throws IOException {
        if (running) return;
        running = true;

        // 1. Démarrer le serveur TCP (en complément de l'UDP fiable)
        tcpServerSocket = new ServerSocket(0);
        myTcpPort = tcpServerSocket.getLocalPort();

        Thread tcpThread = new Thread(this::acceptTcpConnections, "TCP-Chat-Receiver");
        tcpThread.setDaemon(true);
        tcpThread.start();

        // 2. Démarrer le socket Multicast partagé sur DISCOVERY_PORT (44446)
        multicastGroupAddr = InetAddress.getByName(MULTICAST_GROUP);
        multicastSocket = new MulticastSocket(null);
        multicastSocket.setReuseAddress(true);
        multicastSocket.setBroadcast(true);
        multicastSocket.bind(new InetSocketAddress(DISCOVERY_PORT));
        joinMulticastOnAllInterfaces(multicastSocket, multicastGroupAddr);

        Thread multicastThread = new Thread(() -> listenUdpSocket(multicastSocket), "UDP-Multicast-Listener");
        multicastThread.setDaemon(true);
        multicastThread.start();

        // 3. Ouvrir un socket UDP dédié pour l'émission/réception directe et le multi-instance
        for (int i = 1; i < LOCAL_INSTANCE_PORT_COUNT; i++) {
            int candidatePort = DISCOVERY_PORT + i;
            try {
                DatagramSocket ds = new DatagramSocket(null);
                ds.setReuseAddress(false);
                ds.bind(new InetSocketAddress(candidatePort));
                ds.setBroadcast(true);
                localSlotSocket = ds;
                break;
            } catch (IOException ignored) {
            }
        }
        if (localSlotSocket == null) {
            localSlotSocket = new DatagramSocket();
            localSlotSocket.setBroadcast(true);
        }

        Thread unicastThread = new Thread(() -> listenUdpSocket(localSlotSocket), "UDP-Unicast-Listener");
        unicastThread.setDaemon(true);
        unicastThread.start();

        // 4. Planificateur de battements de cœur (Heartbeat) et nettoyage
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "LAN-Heartbeat-Scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::sendHeartbeatIfRegistered, 150, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupStalePeers, 2000, 2000, TimeUnit.MILLISECONDS);

        // 5. Demander immédiatement qui est présent sur le réseau
        sendWhoIsHere();
    }

    /**
     * Vérifie si un nom ou username est disponible sur le réseau local.
     */
    public boolean checkNameAvailability(String candidateName) {
        if (candidateName == null || candidateName.trim().isEmpty()) {
            return false;
        }
        String normalized = candidateName.trim();
        takenNamesDuringCheck.clear();

        for (PeerUser peer : peers.values()) {
            if (peer.getUsername().equalsIgnoreCase(normalized)) {
                return false;
            }
        }

        String encodedName = encode(normalized);
        String checkPacket = "NAME_CHECK|" + myId + "|" + encodedName;

        for (int attempt = 0; attempt < 2; attempt++) {
            broadcastUdpPacket(checkPacket);
            sendWhoIsHere();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (isNameTakenNow(normalized)) {
                return false;
            }
        }

        return !isNameTakenNow(normalized);
    }

    private boolean isNameTakenNow(String candidateName) {
        if (takenNamesDuringCheck.contains(candidateName.toLowerCase())) {
            return true;
        }
        for (PeerUser peer : peers.values()) {
            if (peer.getUsername().equalsIgnoreCase(candidateName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Génère des suggestions de usernames disponibles sur le réseau à partir du nom souhaité.
     */
    public List<String> generateAvailableSuggestions(String baseName) {
        String clean = baseName == null ? "User" : baseName.trim().replaceAll("\\s+", "_");
        if (clean.isEmpty()) clean = "User";

        List<String> suggestions = new ArrayList<>();
        String[] candidates = new String[] {
            clean + "_1",
            clean + "_2",
            clean + "_3",
            clean + "_LAN",
            clean + "_" + (10 + (int) (Math.random() * 89)),
            clean + "_" + (100 + (int) (Math.random() * 899))
        };

        for (String c : candidates) {
            if (!isNameTakenNow(c) && !suggestions.contains(c)) {
                suggestions.add(c);
                if (suggestions.size() >= 3) break;
            }
        }
        return suggestions;
    }

    /**
     * Valide l'inscription avec le nom (ou username) choisi et annonce la présence sur le réseau.
     */
    public void completeRegistration(String chosenUsername) {
        this.myUsername = chosenUsername.trim();
        this.registered = true;
        sendHeartbeatIfRegistered();
        sendWhoIsHere();
    }

    public void sendWhoIsHere() {
        broadcastUdpPacket("WHO_IS_HERE|" + myId);
    }

    private void sendHeartbeatIfRegistered() {
        if (!running || !registered || myUsername == null) return;
        String packet = buildAnnouncePacket("HEARTBEAT");
        broadcastUdpPacket(packet);
    }

    private String buildAnnouncePacket(String command) {
        return command + "|" + myId + "|" + encode(myUsername) + "|" + myTcpPort + "|" + joinTimestamp + "|" + getMyUdpPort();
    }

    private void listenUdpSocket(DatagramSocket socket) {
        byte[] buffer = new byte[8192];
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetAddress senderAddr = packet.getAddress();
                if (!isLocalNetworkAddress(senderAddr)) {
                    continue;
                }

                String raw = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                handleUdpMessage(raw, senderAddr, packet.getPort(), packet.getSocketAddress());
            } catch (IOException e) {
                if (running) {
                    // Ignorer les erreurs temporaires
                }
            }
        }
    }

    private void handleUdpMessage(String raw, InetAddress senderAddr, int senderUdpPort, SocketAddress senderSocketAddr) {
        String[] parts = raw.split("\\|");
        if (parts.length < 2) return;

        String cmd = parts[0];
        String senderId = parts[1];

        if (myId.equals(senderId)) return;

        switch (cmd) {
            case "WHO_IS_HERE" -> {
                if (registered && myUsername != null) {
                    String reply = buildAnnouncePacket("HEARTBEAT");
                    sendDirectUdp(reply, senderSocketAddr);
                    sendDirectUdp(reply, new InetSocketAddress(senderAddr, DISCOVERY_PORT));
                }
            }
            case "NAME_CHECK" -> {
                if (parts.length >= 3 && registered && myUsername != null) {
                    String queriedName = decode(parts[2]);
                    if (myUsername.equalsIgnoreCase(queriedName)) {
                        String takenReply = buildAnnouncePacket("NAME_TAKEN");
                        sendDirectUdp(takenReply, senderSocketAddr);
                        sendDirectUdp(takenReply, new InetSocketAddress(senderAddr, DISCOVERY_PORT));
                        broadcastUdpPacket(takenReply);
                    } else {
                        String announce = buildAnnouncePacket("HEARTBEAT");
                        sendDirectUdp(announce, senderSocketAddr);
                        sendDirectUdp(announce, new InetSocketAddress(senderAddr, DISCOVERY_PORT));
                    }
                }
            }
            case "NAME_TAKEN" -> {
                if (parts.length >= 5) {
                    String peerName = decode(parts[2]);
                    int peerTcpPort = parseIntSafe(parts[3], -1);
                    long peerJoinTime = parseLongSafe(parts[4], System.currentTimeMillis());
                    int peerUdp = parts.length >= 6 ? parseIntSafe(parts[5], senderUdpPort) : senderUdpPort;
                    takenNamesDuringCheck.add(peerName.toLowerCase());
                    if (peerTcpPort > 0) {
                        registerOrUpdatePeer(senderId, peerName, senderAddr, peerTcpPort, peerUdp, peerJoinTime);
                    }
                }
            }
            case "HEARTBEAT" -> {
                if (parts.length >= 5) {
                    String peerName = decode(parts[2]);
                    int peerTcpPort = parseIntSafe(parts[3], -1);
                    long peerJoinTime = parseLongSafe(parts[4], System.currentTimeMillis());
                    int peerUdp = parts.length >= 6 ? parseIntSafe(parts[5], senderUdpPort) : senderUdpPort;

                    if (peerTcpPort > 0) {
                        if (registered && myUsername != null && myUsername.equalsIgnoreCase(peerName)) {
                            if (peerJoinTime < this.joinTimestamp || (peerJoinTime == this.joinTimestamp && senderId.compareTo(myId) < 0)) {
                                registered = false;
                                for (NetworkListener l : listeners) {
                                    l.onNameConflictDetected(myUsername);
                                }
                            } else {
                                String takenReply = buildAnnouncePacket("NAME_TAKEN");
                                sendDirectUdp(takenReply, senderSocketAddr);
                                sendDirectUdp(takenReply, new InetSocketAddress(senderAddr, DISCOVERY_PORT));
                            }
                        }
                        boolean wasUnknown = !peers.containsKey(senderId);
                        registerOrUpdatePeer(senderId, peerName, senderAddr, peerTcpPort, peerUdp, peerJoinTime);
                        // Si on vient de découvrir ce collègue, lui répondre directement en unicast pour qu'il nous voie aussi immédiatement
                        if (wasUnknown && registered && myUsername != null) {
                            String reply = buildAnnouncePacket("HEARTBEAT");
                            sendDirectUdp(reply, senderSocketAddr);
                            sendDirectUdp(reply, new InetSocketAddress(senderAddr, DISCOVERY_PORT));
                            if (peerUdp > 0) {
                                sendDirectUdp(reply, new InetSocketAddress(senderAddr, peerUdp));
                            }
                        }
                    }
                }
            }
            case "MSG_GENERAL" -> {
                // Format: MSG_GENERAL|senderId|msgId|senderName|tcpPort|joinTime|udpPort|content
                if (parts.length >= 8) {
                    String msgId = parts[2];
                    String senderName = decode(parts[3]);
                    int peerTcpPort = parseIntSafe(parts[4], 0);
                    long peerJoinTime = parseLongSafe(parts[5], System.currentTimeMillis());
                    int peerUdp = parseIntSafe(parts[6], senderUdpPort);
                    String content = decode(parts[7]);

                    if (peerTcpPort > 0) {
                        registerOrUpdatePeer(senderId, senderName, senderAddr, peerTcpPort, peerUdp, peerJoinTime);
                    }
                    // Envoyer un ACK UDP immédiat à l'expéditeur
                    sendUdpAck(msgId, senderAddr, senderUdpPort, peerUdp);

                    // Déduplication : n'afficher le message qu'une seule fois
                    if (processedMessageIds.add(msgId)) {
                        ChatMessage msg = new ChatMessage(senderId, senderName, "ALL", content, false, ChatMessage.Type.GENERAL);
                        for (NetworkListener l : listeners) {
                            l.onMessageReceived(msg);
                        }
                    }
                }
            }
            case "MSG_PRIVATE" -> {
                // Format: MSG_PRIVATE|senderId|msgId|targetId|senderName|tcpPort|joinTime|udpPort|content
                if (parts.length >= 9) {
                    String msgId = parts[2];
                    String targetId = parts[3];
                    String senderName = decode(parts[4]);
                    int peerTcpPort = parseIntSafe(parts[5], 0);
                    long peerJoinTime = parseLongSafe(parts[6], System.currentTimeMillis());
                    int peerUdp = parseIntSafe(parts[7], senderUdpPort);
                    String content = decode(parts[8]);

                    if (peerTcpPort > 0) {
                        registerOrUpdatePeer(senderId, senderName, senderAddr, peerTcpPort, peerUdp, peerJoinTime);
                    }

                    // Si ce message privé nous est destiné
                    if (myId.equals(targetId)) {
                        sendUdpAck(msgId, senderAddr, senderUdpPort, peerUdp);
                        if (processedMessageIds.add(msgId)) {
                            ChatMessage msg = new ChatMessage(senderId, senderName, myId, content, false, ChatMessage.Type.PRIVATE);
                            for (NetworkListener l : listeners) {
                                l.onMessageReceived(msg);
                            }
                        }
                    }
                }
            }
            case "MSG_ACK" -> {
                // Format: MSG_ACK|senderId|msgId
                if (parts.length >= 3) {
                    String msgId = parts[2];
                    ackedMessageIds.add(msgId);
                    PeerUser existing = peers.get(senderId);
                    if (existing != null) {
                        existing.updateLastSeen();
                    }
                }
            }
            case "TYPING" -> {
                // Format: TYPING|senderId|senderName|targetId
                if (parts.length >= 4) {
                    String senderName = decode(parts[2]);
                    String targetId = decode(parts[3]);
                    PeerUser existing = peers.get(senderId);
                    if (existing != null) {
                        existing.updateLastSeen();
                    }
                    for (NetworkListener l : listeners) {
                        l.onTypingReceived(senderId, senderName, targetId);
                    }
                }
            }
            case "BYE" -> {
                PeerUser removed = peers.remove(senderId);
                if (removed != null) {
                    for (NetworkListener l : listeners) {
                        l.onPeerLeft(removed);
                        l.onPeersUpdated(getPeers());
                    }
                }
            }
        }
    }

    private void sendUdpAck(String msgId, InetAddress senderAddr, int senderPacketPort, int advertisedUdpPort) {
        String ack = "MSG_ACK|" + myId + "|" + msgId;
        sendDirectUdp(ack, new InetSocketAddress(senderAddr, senderPacketPort));
        sendDirectUdp(ack, new InetSocketAddress(senderAddr, DISCOVERY_PORT));
        if (advertisedUdpPort > 0 && advertisedUdpPort != senderPacketPort) {
            sendDirectUdp(ack, new InetSocketAddress(senderAddr, advertisedUdpPort));
        }
    }

    private void registerOrUpdatePeer(String peerId, String username, InetAddress address, int tcpPort, int udpPort, long peerJoinTime) {
        final boolean[] isNew = {false};
        final boolean[] changed = {false};

        PeerUser peer = peers.compute(peerId, (id, existing) -> {
            if (existing == null) {
                isNew[0] = true;
                changed[0] = true;
                return new PeerUser(id, username, address, tcpPort, udpPort, peerJoinTime);
            } else {
                if (!existing.getUsername().equals(username)
                        || !existing.getAddress().equals(address)
                        || existing.getTcpPort() != tcpPort) {
                    changed[0] = true;
                }
                existing.setUsername(username);
                existing.setAddress(address);
                existing.setTcpPort(tcpPort);
                if (udpPort > 0) {
                    existing.setUdpPort(udpPort);
                }
                existing.updateLastSeen();
                return existing;
            }
        });

        if (isNew[0]) {
            for (NetworkListener l : listeners) {
                l.onPeerJoined(peer);
            }
        }
        if (changed[0]) {
            for (NetworkListener l : listeners) {
                l.onPeersUpdated(getPeers());
            }
        }
    }

    private void cleanupStalePeers() {
        long now = System.currentTimeMillis();
        List<PeerUser> expired = new ArrayList<>();

        peers.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getLastSeen() > PEER_TIMEOUT_MS) {
                expired.add(entry.getValue());
                return true;
            }
            return false;
        });

        if (!expired.isEmpty()) {
            for (PeerUser p : expired) {
                for (NetworkListener l : listeners) {
                    l.onPeerLeft(p);
                }
            }
            for (NetworkListener l : listeners) {
                l.onPeersUpdated(getPeers());
            }
        }
    }

    /**
     * Envoie un message privé à une personne du réseau local.
     * Utilise à la fois UDP (Unicast direct sur le port source du pair + Broadcast LAN avec ACK et retransmission)
     * ET TCP en parallèle pour garantir que le pare-feu Windows ne bloque jamais le message.
     */
    public boolean sendPrivateMessage(String targetPeerId, String content) {
        PeerUser target = peers.get(targetPeerId);
        if (target == null || !isLocalNetworkAddress(target.getAddress())) {
            return false;
        }

        String msgId = UUID.randomUUID().toString();
        processedMessageIds.add(msgId);

        String udpPacket = "MSG_PRIVATE|" + myId + "|" + msgId + "|" + targetPeerId + "|"
                + encode(myUsername) + "|" + myTcpPort + "|" + joinTimestamp + "|" + getMyUdpPort() + "|" + encode(content);

        // Envoi TCP en parallèle (si le port TCP est ouvert)
        String tcpLine = "MSG_PRIVATE|" + myId + "|" + encode(myUsername) + "|" + encode(content) + "|" + msgId;
        Thread.startVirtualThread(() -> {
            if (sendTcpLine(target.getAddress(), target.getTcpPort(), tcpLine)) {
                ackedMessageIds.add(msgId);
            }
        });

        // Envoi UDP fiable avec retransmission rapide jusqu'à réception de MSG_ACK
        for (int attempt = 0; attempt < 4; attempt++) {
            if (ackedMessageIds.contains(msgId)) {
                return true;
            }
            sendUdpToPeerAndBroadcast(target, udpPacket);
            try {
                Thread.sleep(140);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (ackedMessageIds.contains(msgId)) {
                return true;
            }
        }

        // Même si le paquet ACK retour a été retardé, si le pair est actif en UDP le message a été transmis
        return peers.containsKey(targetPeerId);
    }

    /**
     * Envoie un message dans le Salon Général à toutes les personnes du réseau local.
     * Diffuse en UDP (Broadcast + Multicast + Unicast vers chaque pair connu) ET en TCP.
     */
    public int sendGeneralMessage(String content) {
        String msgId = UUID.randomUUID().toString();
        processedMessageIds.add(msgId);

        String udpPacket = "MSG_GENERAL|" + myId + "|" + msgId + "|"
                + encode(myUsername) + "|" + myTcpPort + "|" + joinTimestamp + "|" + getMyUdpPort() + "|" + encode(content);

        String tcpLine = "MSG_GENERAL|" + myId + "|" + encode(myUsername) + "|" + encode(content) + "|" + msgId;
        for (PeerUser peer : peers.values()) {
            if (isLocalNetworkAddress(peer.getAddress())) {
                Thread.startVirtualThread(() -> sendTcpLine(peer.getAddress(), peer.getTcpPort(), tcpLine));
            }
        }

        // Diffuser 3 fois en UDP espacées de 120ms pour garantir 0 perte de paquet sur le Wi-Fi / LAN
        for (int attempt = 0; attempt < 3; attempt++) {
            broadcastUdpPacket(udpPacket);
            if (attempt < 2) {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return peers.size();
    }

    /**
     * Envoie une notification "en train d'écrire..." en UDP (instantané, non bloquant).
     */
    public void sendTypingIndicator(String targetId) {
        if (!registered || myUsername == null) return;
        String udpPacket = "TYPING|" + myId + "|" + encode(myUsername) + "|" + encode(targetId);
        Thread.startVirtualThread(() -> {
            if ("ALL".equals(targetId)) {
                broadcastUdpPacket(udpPacket);
            } else {
                PeerUser target = peers.get(targetId);
                if (target != null) {
                    sendUdpToPeerAndBroadcast(target, udpPacket);
                }
            }
        });
    }

    private void sendUdpToPeerAndBroadcast(PeerUser target, String message) {
        InetAddress addr = target.getAddress();
        if (addr != null && isLocalNetworkAddress(addr)) {
            // 1. Directement sur le port UDP source exact du pair (traverse tous les pare-feu Windows)
            if (target.getUdpPort() > 0) {
                sendDirectUdp(message, new InetSocketAddress(addr, target.getUdpPort()));
            }
            // 2. Directement sur le port de découverte principal (44446) et les slots locaux du pair
            for (int i = 0; i < 4; i++) {
                sendDirectUdp(message, new InetSocketAddress(addr, DISCOVERY_PORT + i));
            }
        }
        // 3. Diffusion Broadcast / Multicast en secours
        broadcastUdpPacket(message);
    }

    private boolean sendTcpLine(InetAddress address, int port, String line) {
        if (!isLocalNetworkAddress(address) || port <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), 1200);
            socket.setSoTimeout(1200);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(line);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void acceptTcpConnections() {
        while (running && tcpServerSocket != null && !tcpServerSocket.isClosed()) {
            try {
                Socket client = tcpServerSocket.accept();
                InetAddress remoteAddr = client.getInetAddress();
                if (!isLocalNetworkAddress(remoteAddr)) {
                    client.close();
                    continue;
                }
                Thread.startVirtualThread(() -> handleTcpClient(client));
            } catch (IOException e) {
                if (running) {
                    // Ignorer à la fermeture
                }
            }
        }
    }

    private void handleTcpClient(Socket client) {
        try (client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            client.setSoTimeout(2000);
            String line = reader.readLine();
            if (line == null) return;

            String[] parts = line.split("\\|");
            if (parts.length < 4) return;

            String type = parts[0];
            String senderId = parts[1];
            String senderName = decode(parts[2]);
            String payload = decode(parts[3]);
            String msgId = parts.length >= 5 ? parts[4] : UUID.randomUUID().toString();

            if (myId.equals(senderId)) return;

            PeerUser existing = peers.get(senderId);
            if (existing != null) {
                existing.updateLastSeen();
            }

            switch (type) {
                case "MSG_PRIVATE" -> {
                    if (processedMessageIds.add(msgId)) {
                        ChatMessage msg = new ChatMessage(senderId, senderName, myId, payload, false, ChatMessage.Type.PRIVATE);
                        for (NetworkListener l : listeners) {
                            l.onMessageReceived(msg);
                        }
                    }
                }
                case "MSG_GENERAL" -> {
                    if (processedMessageIds.add(msgId)) {
                        ChatMessage msg = new ChatMessage(senderId, senderName, "ALL", payload, false, ChatMessage.Type.GENERAL);
                        for (NetworkListener l : listeners) {
                            l.onMessageReceived(msg);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void sendDirectUdp(String message, SocketAddress target) {
        if (target == null) return;
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        if (localSlotSocket != null && !localSlotSocket.isClosed()) {
            try {
                localSlotSocket.send(new DatagramPacket(data, data.length, target));
            } catch (IOException ignored) {
            }
        }
        if (multicastSocket != null && !multicastSocket.isClosed()) {
            try {
                multicastSocket.send(new DatagramPacket(data, data.length, target));
            } catch (IOException ignored) {
            }
        }
    }

    private void broadcastUdpPacket(String message) {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);

        // 1. Envoi Unicast direct à tous les pairs déjà connus sur le réseau (port 44446 + leur port UDP source)
        for (PeerUser peer : peers.values()) {
            InetAddress addr = peer.getAddress();
            if (addr != null && isLocalNetworkAddress(addr)) {
                sendDirectUdp(message, new InetSocketAddress(addr, DISCOVERY_PORT));
                if (peer.getUdpPort() > 0 && peer.getUdpPort() != DISCOVERY_PORT) {
                    sendDirectUdp(message, new InetSocketAddress(addr, peer.getUdpPort()));
                }
            }
        }

        // 2. Envoi sur les adresses de broadcast de CHAQUE interface réseau locale (ex: 10.255.255.255 sur Iris07.local)
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface netIf : Collections.list(interfaces)) {
                    if (!netIf.isUp() || netIf.isLoopback()) continue;
                    for (InterfaceAddress ifAddr : netIf.getInterfaceAddresses()) {
                        InetAddress broadcast = ifAddr.getBroadcast();
                        if (broadcast != null) {
                            sendDirectUdp(message, new InetSocketAddress(broadcast, DISCOVERY_PORT));
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }

        // 3. Broadcast global 255.255.255.255
        try {
            InetAddress globalBroadcast = InetAddress.getByName("255.255.255.255");
            sendDirectUdp(message, new InetSocketAddress(globalBroadcast, DISCOVERY_PORT));
        } catch (IOException ignored) {
        }

        // 4. Envoi au groupe Multicast LAN
        if (multicastGroupAddr != null) {
            sendDirectUdp(message, new InetSocketAddress(multicastGroupAddr, DISCOVERY_PORT));
        }

        // 5. Envoi sur tous les ports locaux (127.0.0.1:44446..44453) pour le multi-instance sur le même PC
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            for (int i = 0; i < LOCAL_INSTANCE_PORT_COUNT; i++) {
                int port = DISCOVERY_PORT + i;
                if (localSlotSocket != null && port == localSlotSocket.getLocalPort()) continue;
                if (localSlotSocket != null && !localSlotSocket.isClosed()) {
                    try {
                        localSlotSocket.send(new DatagramPacket(data, data.length, loopback, port));
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void joinMulticastOnAllInterfaces(MulticastSocket mSocket, InetAddress group) {
        SocketAddress groupAddr = new InetSocketAddress(group, DISCOVERY_PORT);
        boolean joinedAny = false;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface netIf : Collections.list(interfaces)) {
                    if (!netIf.isUp() || !netIf.supportsMulticast()) continue;
                    boolean hasIpv4 = netIf.inetAddresses().anyMatch(a -> a instanceof Inet4Address);
                    if (!hasIpv4) continue;
                    try {
                        mSocket.joinGroup(groupAddr, netIf);
                        String displayName = (netIf.getDisplayName() + " " + netIf.getName()).toLowerCase();
                        if (!displayName.contains("default switch") && !netIf.isLoopback()) {
                            mSocket.setNetworkInterface(netIf);
                        }
                        joinedAny = true;
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
        if (!joinedAny) {
            try {
                mSocket.joinGroup(groupAddr, null);
            } catch (IOException ignored) {
            }
        }
    }

    public void shutdown() {
        if (!running) return;
        if (registered) {
            broadcastUdpPacket("BYE|" + myId);
        }
        running = false;
        registered = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        try {
            if (tcpServerSocket != null) tcpServerSocket.close();
        } catch (IOException ignored) {
        }
        if (multicastSocket != null) multicastSocket.close();
        if (localSlotSocket != null) localSlotSocket.close();
    }

    private static String encode(String text) {
        if (text == null) return "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return encoded;
        }
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLongSafe(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
