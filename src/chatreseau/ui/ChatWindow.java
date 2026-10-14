package chatreseau.ui;

import chatreseau.Main;
import chatreseau.model.ChatMessage;
import chatreseau.model.PeerUser;
import chatreseau.network.NetworkManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fenêtre principale du Chat Réseau Local.
 * Permet de discuter uniquement avec les personnes présentes sur le réseau (en Salon Général ou en Privé).
 */
public class ChatWindow extends JFrame implements NetworkManager.NetworkListener {

    private static final String GENERAL_CHANNEL_ID = "ALL";

    private static final Color SIDEBAR_BG = new Color(15, 23, 42);
    private static final Color SIDEBAR_ITEM_SELECTED = new Color(37, 99, 235);
    private static final Color SIDEBAR_TEXT = new Color(241, 245, 249);
    private static final Color SIDEBAR_SUBTEXT = new Color(148, 163, 184);
    private static final Color CHAT_BG = new Color(241, 245, 249);
    private static final Color MY_BUBBLE_BG = new Color(37, 99, 235);
    private static final Color PEER_BUBBLE_BG = Color.WHITE;
    private static final Color ONLINE_GREEN = new Color(34, 197, 94);

    private final NetworkManager networkManager;
    private String activeConversationId = GENERAL_CHANNEL_ID;

    // Historique des messages par conversation ("ALL" ou peerId)
    private final Map<String, List<ChatMessage>> conversations = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();

    // Modèle de la liste latérale
    private final DefaultListModel<ConversationEntry> contactListModel = new DefaultListModel<>();
    private final JList<ConversationEntry> contactList = new JList<>(contactListModel);

    // Composants d'interface
    private final JLabel myIdentityLabel = new JLabel();
    private final JLabel onlineCountLabel = new JLabel("0 personne sur le réseau");
    private final JLabel chatTitleLabel = new JLabel("Salon Général (Réseau Local)");
    private final JLabel chatSubtitleLabel = new JLabel("Visible uniquement par les personnes connectées au même réseau");
    private final JLabel typingLabel = new JLabel(" ");
    private final JPanel messagesContainer = new JPanel();
    private final JScrollPane messagesScrollPane;
    private final JTextField messageInput = new JTextField();
    private final JButton sendButton = new JButton("Envoyer");

    private Timer typingResetTimer;
    private long lastTypingSentTime = 0;

    public ChatWindow(NetworkManager networkManager) {
        super("Chat Réseau Local — " + networkManager.getMyUsername());
        this.networkManager = networkManager;
        this.networkManager.addListener(this);

        conversations.put(GENERAL_CHANNEL_ID, new ArrayList<>());

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(960, 650);
        setMinimumSize(new Dimension(780, 520));
        setLocationRelativeTo(null);

        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(CHAT_BG);
        messagesContainer.setBorder(new EmptyBorder(16, 20, 16, 20));

        messagesScrollPane = new JScrollPane(messagesContainer);
        messagesScrollPane.setBorder(null);
        messagesScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        initUI();
        refreshContactList(networkManager.getPeers());

        addSystemMessage(GENERAL_CHANNEL_ID,
                "Bienvenue " + networkManager.getMyUsername() + " ! Vous ne pouvez discuter qu'avec les personnes présentes sur votre réseau local.");

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                networkManager.removeListener(ChatWindow.this);
                networkManager.shutdown();
            }
        });
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // ==================== BARRE LATÉRALE GAUCHE ====================
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(295, 0));

        // Profil de l'utilisateur connecté
        JPanel profileHeader = new JPanel();
        profileHeader.setLayout(new BoxLayout(profileHeader, BoxLayout.Y_AXIS));
        profileHeader.setBackground(new Color(30, 41, 59));
        profileHeader.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel appTitle = new JLabel("CHAT RÉSEAU LAN");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        appTitle.setForeground(new Color(96, 165, 250));

        myIdentityLabel.setText("● " + networkManager.getMyUsername());
        myIdentityLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        myIdentityLabel.setForeground(Color.WHITE);

        JLabel ipLabel = new JLabel("IP Locale : " + detectLocalLanIp() + " (Port " + networkManager.getMyTcpPort() + ")");
        ipLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        ipLabel.setForeground(SIDEBAR_SUBTEXT);

        profileHeader.add(appTitle);
        profileHeader.add(Box.createVerticalStrut(4));
        profileHeader.add(myIdentityLabel);
        profileHeader.add(Box.createVerticalStrut(2));
        profileHeader.add(ipLabel);

        // Titre de la liste des personnes du réseau
        JPanel listHeader = new JPanel(new BorderLayout());
        listHeader.setBackground(SIDEBAR_BG);
        listHeader.setBorder(new EmptyBorder(12, 16, 6, 16));

        JLabel peopleHeaderLabel = new JLabel("PERSONNES DU RÉSEAU");
        peopleHeaderLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        peopleHeaderLabel.setForeground(SIDEBAR_SUBTEXT);

        onlineCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        onlineCountLabel.setForeground(ONLINE_GREEN);

        listHeader.add(peopleHeaderLabel, BorderLayout.WEST);
        listHeader.add(onlineCountLabel, BorderLayout.EAST);

        // Liste des contacts du réseau
        contactList.setBackground(SIDEBAR_BG);
        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contactList.setCellRenderer(new ContactCellRenderer());
        contactList.setFixedCellHeight(62);
        contactList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ConversationEntry selected = contactList.getSelectedValue();
                if (selected != null) {
                    switchConversation(selected.id);
                }
            }
        });

        JScrollPane contactScroll = new JScrollPane(contactList);
        contactScroll.setBorder(null);
        contactScroll.getViewport().setBackground(SIDEBAR_BG);

        JPanel centerSidebar = new JPanel(new BorderLayout());
        centerSidebar.setBackground(SIDEBAR_BG);
        centerSidebar.add(listHeader, BorderLayout.NORTH);
        centerSidebar.add(contactScroll, BorderLayout.CENTER);

        // Pied de barre latérale : boutons Actualiser le réseau et Tester une autre personne
        JPanel sidebarFooter = new JPanel();
        sidebarFooter.setLayout(new BoxLayout(sidebarFooter, BoxLayout.Y_AXIS));
        sidebarFooter.setBackground(SIDEBAR_BG);
        sidebarFooter.setBorder(new EmptyBorder(10, 14, 14, 14));

        JButton refreshBtn = new JButton("↻ Actualiser le réseau");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        refreshBtn.setBackground(new Color(30, 58, 138));
        refreshBtn.setForeground(SIDEBAR_TEXT);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setBorder(new EmptyBorder(8, 12, 8, 12));
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        refreshBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        refreshBtn.addActionListener(e -> Thread.startVirtualThread(networkManager::sendWhoIsHere));

        JButton testInstanceBtn = new JButton("+ Tester une autre personne (même PC)");
        testInstanceBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        testInstanceBtn.setBackground(new Color(51, 65, 85));
        testInstanceBtn.setForeground(SIDEBAR_TEXT);
        testInstanceBtn.setFocusPainted(false);
        testInstanceBtn.setBorder(new EmptyBorder(8, 12, 8, 12));
        testInstanceBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        testInstanceBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        testInstanceBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        testInstanceBtn.addActionListener(e -> Main.launchNewClientInstance());

        sidebarFooter.add(refreshBtn);
        sidebarFooter.add(Box.createVerticalStrut(6));
        sidebarFooter.add(testInstanceBtn);

        sidebar.add(profileHeader, BorderLayout.NORTH);
        sidebar.add(centerSidebar, BorderLayout.CENTER);
        sidebar.add(sidebarFooter, BorderLayout.SOUTH);

        // ==================== ZONE DE CONVERSATION DROITE ====================
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(CHAT_BG);

        // En-tête de la conversation active
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(Color.WHITE);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                new EmptyBorder(14, 20, 14, 20)
        ));

        JPanel headerTitles = new JPanel();
        headerTitles.setLayout(new BoxLayout(headerTitles, BoxLayout.Y_AXIS));
        headerTitles.setOpaque(false);

        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        chatTitleLabel.setForeground(new Color(15, 23, 42));

        chatSubtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chatSubtitleLabel.setForeground(new Color(100, 116, 139));

        headerTitles.add(chatTitleLabel);
        headerTitles.add(Box.createVerticalStrut(2));
        headerTitles.add(chatSubtitleLabel);

        typingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        typingLabel.setForeground(SIDEBAR_ITEM_SELECTED);

        chatHeader.add(headerTitles, BorderLayout.WEST);
        chatHeader.add(typingLabel, BorderLayout.EAST);

        // Barre d'écriture en bas
        JPanel inputBar = new JPanel(new BorderLayout(10, 0));
        inputBar.setBackground(Color.WHITE);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                new EmptyBorder(12, 20, 12, 20)
        ));

        messageInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));
        messageInput.addActionListener(e -> sendCurrentMessage());
        messageInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notifyTyping();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendButton.setBackground(MY_BUBBLE_BG);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new EmptyBorder(10, 22, 10, 22));
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendCurrentMessage());

        inputBar.add(messageInput, BorderLayout.CENTER);
        inputBar.add(sendButton, BorderLayout.EAST);

        rightPanel.add(chatHeader, BorderLayout.NORTH);
        rightPanel.add(messagesScrollPane, BorderLayout.CENTER);
        rightPanel.add(inputBar, BorderLayout.SOUTH);

        add(sidebar, BorderLayout.WEST);
        add(rightPanel, BorderLayout.CENTER);

        typingResetTimer = new Timer(2500, e -> typingLabel.setText(" "));
        typingResetTimer.setRepeats(false);
    }

    private void notifyTyping() {
        long now = System.currentTimeMillis();
        if (now - lastTypingSentTime > 1200 && !messageInput.getText().trim().isEmpty()) {
            lastTypingSentTime = now;
            networkManager.sendTypingIndicator(activeConversationId);
        }
    }

    private void switchConversation(String conversationId) {
        this.activeConversationId = conversationId;
        unreadCounts.put(conversationId, 0);
        typingLabel.setText(" ");

        // Synchroniser la sélection dans la liste latérale
        for (int i = 0; i < contactListModel.size(); i++) {
            if (contactListModel.get(i).id.equals(conversationId)) {
                if (contactList.getSelectedIndex() != i) {
                    contactList.setSelectedIndex(i);
                }
                break;
            }
        }

        if (GENERAL_CHANNEL_ID.equals(conversationId)) {
            chatTitleLabel.setText("Salon Général (Réseau Local)");
            int count = networkManager.getPeers().size();
            chatSubtitleLabel.setText(count == 0
                    ? "En attente d'autres personnes sur le réseau local..."
                    : count + (count > 1 ? " personnes connectées sur le réseau" : " personne connectée sur le réseau"));
            messageInput.setEnabled(true);
            sendButton.setEnabled(true);
        } else {
            PeerUser peer = networkManager.getPeer(conversationId);
            if (peer != null) {
                chatTitleLabel.setText(peer.getUsername());
                chatSubtitleLabel.setText("En ligne sur le réseau local • " + peer.getAddress().getHostAddress() + ":" + peer.getTcpPort());
                messageInput.setEnabled(true);
                sendButton.setEnabled(true);
            } else {
                chatTitleLabel.setText("Utilisateur hors du réseau");
                chatSubtitleLabel.setText("Cette personne a quitté le réseau local — discussion indisponible");
                messageInput.setEnabled(false);
                sendButton.setEnabled(false);
            }
        }

        contactList.repaint();
        renderActiveConversationMessages();
        messageInput.requestFocusInWindow();
    }

    /**
     * Ramène la fenêtre de chat au premier plan et sélectionne la conversation voulue.
     * Appelé lors du clic sur une notification toast Discord.
     */
    public void bringToFrontAndSelect(String convId) {
        SwingUtilities.invokeLater(() -> {
            if (getState() == JFrame.ICONIFIED) {
                setState(JFrame.NORMAL);
            }
            setVisible(true);
            toFront();
            requestFocus();
            switchConversation(convId);
        });
    }

    private void sendCurrentMessage() {
        String content = messageInput.getText().trim();
        if (content.isEmpty()) return;

        if (GENERAL_CHANNEL_ID.equals(activeConversationId)) {
            messageInput.setText("");
            ChatMessage myMsg = new ChatMessage(
                    networkManager.getMyId(),
                    networkManager.getMyUsername(),
                    GENERAL_CHANNEL_ID,
                    content,
                    true,
                    ChatMessage.Type.GENERAL
            );
            appendMessage(GENERAL_CHANNEL_ID, myMsg);
            Thread.startVirtualThread(() -> networkManager.sendGeneralMessage(content));
        } else {
            PeerUser target = networkManager.getPeer(activeConversationId);
            if (target == null) {
                JOptionPane.showMessageDialog(
                        this,
                        "Cette personne n'est plus sur le réseau local.",
                        "Destinataire hors réseau",
                        JOptionPane.WARNING_MESSAGE
                );
                switchConversation(GENERAL_CHANNEL_ID);
                return;
            }

            messageInput.setText("");
            String targetId = target.getId();
            ChatMessage myMsg = new ChatMessage(
                    networkManager.getMyId(),
                    networkManager.getMyUsername(),
                    targetId,
                    content,
                    true,
                    ChatMessage.Type.PRIVATE
            );
            appendMessage(targetId, myMsg);

            Thread.startVirtualThread(() -> {
                boolean delivered = networkManager.sendPrivateMessage(targetId, content);
                if (!delivered) {
                    SwingUtilities.invokeLater(() -> addSystemMessage(
                            targetId,
                            "Message non délivré : " + target.getUsername() + " n'est plus joignable sur le réseau."
                    ));
                }
            });
        }
    }

    private void addSystemMessage(String conversationId, String text) {
        ChatMessage sysMsg = new ChatMessage("SYSTEM", "Système", conversationId, text, false, ChatMessage.Type.SYSTEM);
        appendMessage(conversationId, sysMsg);
    }

    private void appendMessage(String conversationId, ChatMessage message) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);

        if (conversationId.equals(activeConversationId)) {
            messagesContainer.add(createMessageBubble(message));
            messagesContainer.add(Box.createVerticalStrut(10));
            messagesContainer.revalidate();
            messagesContainer.repaint();
            scrollToBottom();
        } else {
            unreadCounts.put(conversationId, unreadCounts.getOrDefault(conversationId, 0) + 1);
            contactList.repaint();
        }
    }

    private void renderActiveConversationMessages() {
        messagesContainer.removeAll();
        List<ChatMessage> list = conversations.getOrDefault(activeConversationId, Collections.emptyList());
        for (ChatMessage msg : list) {
            messagesContainer.add(createMessageBubble(msg));
            messagesContainer.add(Box.createVerticalStrut(10));
        }
        messagesContainer.revalidate();
        messagesContainer.repaint();
        scrollToBottom();
    }

    private JPanel createMessageBubble(ChatMessage msg) {
        if (msg.getType() == ChatMessage.Type.SYSTEM) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
            row.setOpaque(false);
            JLabel badge = new JLabel(msg.getContent());
            badge.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            badge.setForeground(new Color(71, 85, 105));
            badge.setOpaque(true);
            badge.setBackground(new Color(226, 232, 240));
            badge.setBorder(new EmptyBorder(5, 14, 5, 14));
            row.add(badge);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            return row;
        }

        boolean isMe = msg.isFromMe();
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(isMe ? MY_BUBBLE_BG : PEER_BUBBLE_BG);
        bubble.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isMe ? MY_BUBBLE_BG : new Color(226, 232, 240), 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));

        String headerText = (isMe ? "Vous" : msg.getSenderName()) + " • " + msg.getTimestamp();
        JLabel metaLabel = new JLabel(headerText);
        metaLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        metaLabel.setForeground(isMe ? new Color(191, 219, 254) : new Color(100, 116, 139));

        String safeHtml = escapeHtml(msg.getContent());
        JLabel contentLabel = new JLabel("<html><body style='width: 340px; font-family: Segoe UI; font-size: 11pt;'>" + safeHtml + "</body></html>");
        contentLabel.setForeground(isMe ? Color.WHITE : new Color(15, 23, 42));

        bubble.add(metaLabel);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(contentLabel);

        row.add(bubble);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, bubble.getPreferredSize().height + 12));
        return row;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = messagesScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void refreshContactList(Collection<PeerUser> peers) {
        List<PeerUser> sorted = new ArrayList<>(peers);
        sorted.sort(Comparator.comparing(PeerUser::getUsername, String.CASE_INSENSITIVE_ORDER));

        String previousSelection = activeConversationId;
        contactListModel.clear();

        // 1. Salon Général du réseau
        contactListModel.addElement(new ConversationEntry(
                GENERAL_CHANNEL_ID,
                "Salon Général (Réseau)",
                sorted.isEmpty() ? "En attente de participants..." : sorted.size() + " personne(s) en ligne",
                true
        ));

        // 2. Personnes connectées sur le réseau local
        for (PeerUser peer : sorted) {
            contactListModel.addElement(new ConversationEntry(
                    peer.getId(),
                    peer.getUsername(),
                    peer.getAddress().getHostAddress(),
                    false
            ));
        }

        onlineCountLabel.setText(sorted.size() + (sorted.size() > 1 ? " connectés" : " connecté"));

        // Restaurer la sélection
        int indexToSelect = 0;
        for (int i = 0; i < contactListModel.size(); i++) {
            if (contactListModel.get(i).id.equals(previousSelection)) {
                indexToSelect = i;
                break;
            }
        }
        contactList.setSelectedIndex(indexToSelect);

        if (GENERAL_CHANNEL_ID.equals(activeConversationId)) {
            int count = sorted.size();
            chatSubtitleLabel.setText(count == 0
                    ? "En attente d'autres personnes sur le réseau local..."
                    : count + (count > 1 ? " personnes connectées sur le réseau" : " personne connectée sur le réseau"));
        }
    }

    // ==================== ÉVÉNEMENTS RÉSEAU ====================

    @Override
    public void onPeersUpdated(Collection<PeerUser> peers) {
        SwingUtilities.invokeLater(() -> refreshContactList(peers));
    }

    @Override
    public void onPeerJoined(PeerUser peer) {
        SwingUtilities.invokeLater(() -> {
            addSystemMessage(GENERAL_CHANNEL_ID, peer.getUsername() + " a rejoint le réseau local (" + peer.getAddress().getHostAddress() + ").");
            if (!isActive() || !isFocused()) {
                NotificationToast toast = new NotificationToast(
                        peer.getUsername(),
                        "• Réseau Local",
                        "A rejoint le réseau local",
                        () -> bringToFrontAndSelect(peer.getId())
                );
                toast.showNotification();
            }
        });
    }

    @Override
    public void onPeerLeft(PeerUser peer) {
        SwingUtilities.invokeLater(() -> {
            addSystemMessage(GENERAL_CHANNEL_ID, peer.getUsername() + " a quitté le réseau local.");
            if (conversations.containsKey(peer.getId())) {
                addSystemMessage(peer.getId(), peer.getUsername() + " s'est déconnecté du réseau.");
            }
            if (peer.getId().equals(activeConversationId)) {
                switchConversation(activeConversationId);
            }
        });
    }

    @Override
    public void onMessageReceived(ChatMessage message) {
        SwingUtilities.invokeLater(() -> {
            typingLabel.setText(" ");
            String convId = (message.getType() == ChatMessage.Type.GENERAL)
                    ? GENERAL_CHANNEL_ID
                    : message.getSenderId();
            appendMessage(convId, message);

            // Notification Discord en bas à droite si le message ne vient pas de nous
            if (!message.isFromMe()) {
                boolean isCurrentConv = convId.equals(activeConversationId);
                boolean isWindowFocused = isActive() && isFocused();

                // Notifier si la fenêtre est minimisée, en arrière-plan, ou si on consulte un autre salon
                if (!isWindowFocused || !isCurrentConv) {
                    String title = message.getSenderName();
                    String subtitle = (message.getType() == ChatMessage.Type.GENERAL) ? "• Salon Général" : "• Message Privé";
                    NotificationToast toast = new NotificationToast(
                            title,
                            subtitle,
                            message.getContent(),
                            () -> bringToFrontAndSelect(convId)
                    );
                    toast.showNotification();
                }
            }
        });
    }

    @Override
    public void onTypingReceived(String senderId, String senderName, String targetId) {
        SwingUtilities.invokeLater(() -> {
            boolean relevant = (GENERAL_CHANNEL_ID.equals(targetId) && GENERAL_CHANNEL_ID.equals(activeConversationId))
                    || (!GENERAL_CHANNEL_ID.equals(targetId) && senderId.equals(activeConversationId));
            if (relevant) {
                typingLabel.setText(senderName + " est en train d'écrire...");
                typingResetTimer.restart();
            }
        });
    }

    @Override
    public void onNameConflictDetected(String conflictingName) {
        SwingUtilities.invokeLater(() -> {
            LoginDialog conflictDialog = new LoginDialog(this, networkManager, conflictingName);
            String newUsername = conflictDialog.showDialog();
            if (newUsername != null) {
                setTitle("Chat Réseau Local — " + newUsername);
                myIdentityLabel.setText("● " + newUsername);
                addSystemMessage(GENERAL_CHANNEL_ID, "Votre username est désormais « " + newUsername + " ».");
            }
        });
    }

    // ==================== CLASSES INTERNES D'AFFICHAGE ====================

    private record ConversationEntry(String id, String title, String subtitle, boolean isGeneral) {
    }

    private class ContactCellRenderer extends JPanel implements ListCellRenderer<ConversationEntry> {
        private final JLabel titleLbl = new JLabel();
        private final JLabel subLbl = new JLabel();
        private final JLabel badgeLbl = new JLabel();

        public ContactCellRenderer() {
            setLayout(new BorderLayout(8, 2));
            setBorder(new EmptyBorder(10, 16, 10, 16));

            JPanel textStack = new JPanel();
            textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
            textStack.setOpaque(false);

            titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));

            textStack.add(titleLbl);
            textStack.add(Box.createVerticalStrut(2));
            textStack.add(subLbl);

            badgeLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            badgeLbl.setForeground(Color.WHITE);
            badgeLbl.setBackground(new Color(239, 68, 68));
            badgeLbl.setOpaque(true);
            badgeLbl.setBorder(new EmptyBorder(2, 7, 2, 7));
            badgeLbl.setHorizontalAlignment(SwingConstants.CENTER);

            add(textStack, BorderLayout.CENTER);
            add(badgeLbl, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ConversationEntry> list,
                                                      ConversationEntry value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            setBackground(isSelected ? SIDEBAR_ITEM_SELECTED : SIDEBAR_BG);

            String prefix = value.isGeneral ? "# " : "● ";
            titleLbl.setText(prefix + value.title);
            titleLbl.setForeground(Color.WHITE);

            subLbl.setText(value.subtitle);
            subLbl.setForeground(isSelected ? new Color(219, 234, 254) : SIDEBAR_SUBTEXT);

            int unread = unreadCounts.getOrDefault(value.id, 0);
            if (unread > 0) {
                badgeLbl.setText(String.valueOf(unread));
                badgeLbl.setVisible(true);
            } else {
                badgeLbl.setVisible(false);
            }
            return this;
        }
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String detectLocalLanIp() {
        return NetworkManager.detectBestLanIp();
    }
}
