package chatreseau.model;

import java.net.InetAddress;
import java.util.Objects;

/**
 * Représente un utilisateur découvert sur le réseau local (LAN).
 */
public class PeerUser {
    private final String id;
    private String username;
    private InetAddress address;
    private int tcpPort;
    private int udpPort;
    private final long joinTimestamp;
    private long lastSeen;

    public PeerUser(String id, String username, InetAddress address, int tcpPort, int udpPort, long joinTimestamp) {
        this.id = id;
        this.username = username;
        this.address = address;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.joinTimestamp = joinTimestamp;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public long getJoinTimestamp() {
        return joinTimestamp;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerUser peerUser)) return false;
        return Objects.equals(id, peerUser.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return username + " (" + address.getHostAddress() + ")";
    }
}
