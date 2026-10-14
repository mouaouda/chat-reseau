package chatreseau.ui;

import chatreseau.network.NetworkManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Fenêtre d'inscription au Chat Réseau.
 * - Demande uniquement le NOM à l'inscription.
 * - Si ce nom est indisponible (déjà utilisé sur le réseau local), demande un USERNAME alternatif
 *   et propose des suggestions disponibles.
 */
public class LoginDialog extends JDialog {

    private static final Color PRIMARY_COLOR = new Color(37, 99, 235);
    private static final Color BG_COLOR = new Color(248, 250, 252);
    private static final Color CARD_COLOR = Color.WHITE;
    private static final Color ERROR_BG = new Color(254, 242, 242);
    private static final Color ERROR_TEXT = new Color(185, 28, 28);
    private static final Color MUTED_TEXT = new Color(100, 116, 139);

    private final NetworkManager networkManager;
    private String validatedIdentity = null;
    private boolean usernameFallbackActive = false;

    private final JTextField nameField = new JTextField();
    private final JPanel usernamePanel = new JPanel();
    private final JTextField usernameField = new JTextField();
    private final JPanel suggestionsBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private final JLabel statusLabel = new JLabel(" ");
    private final ModernButton submitButton = new ModernButton("Rejoindre le chat reseau", PRIMARY_COLOR);

    public LoginDialog(Frame parent, NetworkManager networkManager, String initialConflictName) {
        super(parent, "Inscription - Chat Réseau Local", true);
        this.networkManager = networkManager;

        initUI();

        if (initialConflictName != null && !initialConflictName.isBlank()) {
            nameField.setText(initialConflictName);
            activateUsernameFallback(initialConflictName);
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                validatedIdentity = null;
                dispose();
            }
        });
    }

    private void initUI() {
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(BG_COLOR);
        setLayout(new BorderLayout());

        JPanel mainCard = new JPanel();
        mainCard.setLayout(new BoxLayout(mainCard, BoxLayout.Y_AXIS));
        mainCard.setBackground(CARD_COLOR);
        mainCard.setBorder(new EmptyBorder(28, 32, 28, 32));

        // En-tête
        JLabel badgeLabel = new JLabel("RÉSEAU LOCAL (LAN UNIQUEMENT)", SwingConstants.LEFT);
        badgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badgeLabel.setForeground(PRIMARY_COLOR);
        badgeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("Inscription");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(new Color(15, 23, 42));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Entrez simplement votre nom pour discuter avec les personnes du réseau.");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitleLabel.setForeground(MUTED_TEXT);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        mainCard.add(badgeLabel);
        mainCard.add(Box.createVerticalStrut(4));
        mainCard.add(titleLabel);
        mainCard.add(Box.createVerticalStrut(4));
        mainCard.add(subtitleLabel);
        mainCard.add(Box.createVerticalStrut(20));

        // Champ unique demandé à l'inscription : Le Nom
        JLabel nameLabel = new JLabel("Votre nom :");
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        nameLabel.setForeground(new Color(30, 41, 59));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        styleTextField(nameField);
        nameField.addActionListener(e -> onSubmit());

        mainCard.add(nameLabel);
        mainCard.add(Box.createVerticalStrut(6));
        mainCard.add(nameField);

        // Section Username alternatif (cachée par défaut, affichée uniquement si le nom est indisponible sur le réseau)
        usernamePanel.setLayout(new BoxLayout(usernamePanel, BoxLayout.Y_AXIS));
        usernamePanel.setBackground(ERROR_BG);
        usernamePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(252, 165, 165), 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));
        usernamePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        usernamePanel.setVisible(false);

        JLabel conflictTitle = new JLabel("Ce nom est indisponible (déjà utilisé sur le réseau) !");
        conflictTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        conflictTitle.setForeground(ERROR_TEXT);
        conflictTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel usernameLabel = new JLabel("Choisissez un username (pseudo) disponible :");
        usernameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        usernameLabel.setForeground(new Color(127, 29, 29));
        usernameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        styleTextField(usernameField);
        usernameField.addActionListener(e -> onSubmit());

        JLabel suggestTitle = new JLabel("Suggestions disponibles :");
        suggestTitle.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        suggestTitle.setForeground(MUTED_TEXT);
        suggestTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        suggestionsBox.setOpaque(false);
        suggestionsBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        usernamePanel.add(conflictTitle);
        usernamePanel.add(Box.createVerticalStrut(6));
        usernamePanel.add(usernameLabel);
        usernamePanel.add(Box.createVerticalStrut(6));
        usernamePanel.add(usernameField);
        usernamePanel.add(Box.createVerticalStrut(8));
        usernamePanel.add(suggestTitle);
        usernamePanel.add(suggestionsBox);

        mainCard.add(Box.createVerticalStrut(14));
        mainCard.add(usernamePanel);

        // Message d'état / chargement
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED_TEXT);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        mainCard.add(Box.createVerticalStrut(10));
        mainCard.add(statusLabel);
        mainCard.add(Box.createVerticalStrut(12));

        // Bouton de validation ModernButton
        submitButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        submitButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        submitButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        submitButton.setPreferredSize(new Dimension(380, 44));
        submitButton.addActionListener(e -> onSubmit());

        mainCard.add(submitButton);

        add(mainCard, BorderLayout.CENTER);
        pack();
        setMinimumSize(new Dimension(460, getPreferredSize().height));
        setLocationRelativeTo(getOwner());
    }

    private void styleTextField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        field.setPreferredSize(new Dimension(380, 38));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void onSubmit() {
        String candidate;
        if (!usernameFallbackActive) {
            candidate = nameField.getText().trim();
            if (candidate.isEmpty()) {
                statusLabel.setForeground(ERROR_TEXT);
                statusLabel.setText("Veuillez entrer votre nom.");
                nameField.requestFocusInWindow();
                return;
            }
        } else {
            candidate = usernameField.getText().trim();
            if (candidate.isEmpty()) {
                statusLabel.setForeground(ERROR_TEXT);
                statusLabel.setText("Veuillez saisir ou sélectionner un username.");
                usernameField.requestFocusInWindow();
                return;
            }
        }

        if (candidate.length() > 30) {
            statusLabel.setForeground(ERROR_TEXT);
            statusLabel.setText("Le nom ne doit pas dépasser 30 caractères.");
            return;
        }

        // Désactiver les champs pendant la vérification sur le réseau local
        setBusy(true, "Vérification de la disponibilité de « " + candidate + " » sur le réseau...");

        final String checkTarget = candidate;
        Thread.startVirtualThread(() -> {
            boolean available = networkManager.checkNameAvailability(checkTarget);
            SwingUtilities.invokeLater(() -> {
                setBusy(false, " ");
                if (available) {
                    validatedIdentity = checkTarget;
                    networkManager.completeRegistration(checkTarget);
                    dispose();
                } else {
                    activateUsernameFallback(checkTarget);
                }
            });
        });
    }

    private void activateUsernameFallback(String unavailableName) {
        usernameFallbackActive = true;
        nameField.setEditable(false);
        nameField.setBackground(new Color(241, 245, 249));

        List<String> suggestions = networkManager.generateAvailableSuggestions(nameField.getText().trim());
        suggestionsBox.removeAll();
        for (String suggestion : suggestions) {
            ModernButton chip = new ModernButton("Utiliser " + suggestion, new Color(30, 58, 138));
            chip.setFont(new Font("Segoe UI", Font.BOLD, 12));
            chip.setPreferredSize(new Dimension(150, 32));
            chip.addActionListener(e -> {
                usernameField.setText(suggestion);
                usernameField.requestFocusInWindow();
            });
            suggestionsBox.add(chip);
        }

        if (!suggestions.isEmpty() && usernameField.getText().trim().isEmpty()) {
            usernameField.setText(suggestions.get(0));
        }

        statusLabel.setForeground(ERROR_TEXT);
        statusLabel.setText("« " + unavailableName + " » est déjà pris sur le réseau. Utilisez un username disponible.");
        submitButton.setText("Valider ce username");

        usernamePanel.setVisible(true);
        pack();
        setMinimumSize(new Dimension(460, getPreferredSize().height));
        setLocationRelativeTo(getOwner());
        usernameField.requestFocusInWindow();
        usernameField.selectAll();
    }

    private void setBusy(boolean busy, String statusText) {
        nameField.setEnabled(!busy);
        usernameField.setEnabled(!busy);
        submitButton.setEnabled(!busy);
        statusLabel.setForeground(MUTED_TEXT);
        statusLabel.setText(statusText);
    }

    public String showDialog() {
        setVisible(true);
        return validatedIdentity;
    }
}
