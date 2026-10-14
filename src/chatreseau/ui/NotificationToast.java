package chatreseau.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Toast de notification moderne style Discord / Windows en bas à droite de l'écran.
 * - Ne vole pas le focus clavier
 * - Empilement automatique des notifications
 * - Son de notification doux style Discord (pur Java)
 * - Clic pour ouvrir directement le chat sur la conversation
 */
public class NotificationToast extends JWindow {

    private static final int TOAST_WIDTH = 340;
    private static final int TOAST_HEIGHT = 82;
    private static final int PADDING_EDGE = 16;
    private static final int STACK_SPACING = 10;
    private static final int DISPLAY_DURATION_MS = 4500;

    // Couleurs modernes Discord
    private static final Color BG_DARK = new Color(30, 31, 34);       // #1e1f22
    private static final Color BORDER_COLOR = new Color(53, 55, 60);  // bordure subtile
    private static final Color DISCORD_BLURPLE = new Color(88, 101, 242); // #5865F2
    private static final Color TEXT_WHITE = new Color(242, 243, 245);
    private static final Color TEXT_MUTED = new Color(148, 155, 164);

    // Liste des toasts actifs affichés à l'écran
    private static final List<NotificationToast> ACTIVE_TOASTS = new CopyOnWriteArrayList<>();

    private final Runnable onClickAction;
    private final Timer dismissTimer;
    private Timer fadeTimer;
    private float opacity = 0.0f;
    private boolean isClosing = false;

    public NotificationToast(String title, String subtitle, String message, Runnable onClickAction) {
        this.onClickAction = onClickAction;

        setAlwaysOnTop(true);
        setFocusableWindowState(false); // Très important : ne vole pas le focus de l'utilisateur qui tape ailleurs
        setSize(TOAST_WIDTH, TOAST_HEIGHT);

        // Forme arrondie
        try {
            setShape(new RoundRectangle2D.Double(0, 0, TOAST_WIDTH, TOAST_HEIGHT, 16, 16));
        } catch (Exception ignored) {
        }

        JPanel root = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Fond sombre Discord
                g2.setColor(BG_DARK);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                // Bordure
                g2.setColor(BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                // Petite ligne d'accentuation blurple à gauche
                g2.setColor(DISCORD_BLURPLE);
                g2.fillRoundRect(0, 6, 4, getHeight() - 12, 4, 4);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(10, 14, 10, 12));
        root.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 1. Avatar / Initiale à gauche
        String initial = (title != null && !title.isEmpty()) ? title.substring(0, 1).toUpperCase() : "#";
        JPanel avatar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DISCORD_BLURPLE);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
                var fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(initial)) / 2;
                int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2.drawString(initial, x, y);
                g2.dispose();
            }
        };
        avatar.setOpaque(false);
        avatar.setPreferredSize(new Dimension(42, 42));
        avatar.setMaximumSize(new Dimension(42, 42));

        // 2. Texte au centre
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        // Ligne supérieure : Titre + Sous-titre
        JPanel titleRow = new JPanel(new BorderLayout(6, 0));
        titleRow.setOpaque(false);

        JLabel titleLbl = new JLabel(truncate(title, 22));
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLbl.setForeground(TEXT_WHITE);

        JLabel subLbl = new JLabel(subtitle != null ? subtitle : "");
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subLbl.setForeground(DISCORD_BLURPLE);

        titleRow.add(titleLbl, BorderLayout.WEST);
        titleRow.add(subLbl, BorderLayout.CENTER);

        // Ligne inférieure : Aperçu du message
        JLabel msgLbl = new JLabel(truncate(message, 42));
        msgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        msgLbl.setForeground(TEXT_MUTED);

        textPanel.add(titleRow);
        textPanel.add(Box.createVerticalStrut(3));
        textPanel.add(msgLbl);

        // 3. Bouton Fermer "✕" à droite
        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.setForeground(TEXT_MUTED);
        closeBtn.setOpaque(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(new EmptyBorder(0, 4, 0, 0));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> closeToast());

        closeBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeBtn.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeBtn.setForeground(TEXT_MUTED);
            }
        });

        root.add(avatar, BorderLayout.WEST);
        root.add(textPanel, BorderLayout.CENTER);
        root.add(closeBtn, BorderLayout.EAST);

        // Clic sur toute la zone pour ouvrir le chat
        MouseAdapter clickAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isClosing) {
                    closeToast();
                    if (onClickAction != null) {
                        SwingUtilities.invokeLater(onClickAction);
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                // Pause le chrono de fermeture si la souris survole la notification
                if (dismissTimer.isRunning()) {
                    dismissTimer.stop();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Reprend le chrono de fermeture
                if (!isClosing) {
                    dismissTimer.restart();
                }
            }
        };

        root.addMouseListener(clickAdapter);
        textPanel.addMouseListener(clickAdapter);
        titleRow.addMouseListener(clickAdapter);
        titleLbl.addMouseListener(clickAdapter);
        subLbl.addMouseListener(clickAdapter);
        msgLbl.addMouseListener(clickAdapter);
        avatar.addMouseListener(clickAdapter);

        setContentPane(root);

        // Minuteur de fermeture automatique
        dismissTimer = new Timer(DISPLAY_DURATION_MS, e -> closeToast());
        dismissTimer.setRepeats(false);
    }

    /**
     * Affiche la notification à l'écran avec fondu et la place au bon niveau d'empilement.
     */
    public void showNotification() {
        SwingUtilities.invokeLater(() -> {
            ACTIVE_TOASTS.add(this);
            recalculatePositions();

            // Jouer le son de notification Discord en arrière-plan
            playDiscordChime();

            // Animation de fondu (Fade-in)
            try {
                setOpacity(0.0f);
            } catch (Exception ignored) {
            }
            setVisible(true);

            fadeTimer = new Timer(15, e -> {
                opacity += 0.1f;
                if (opacity >= 0.98f) {
                    opacity = 1.0f;
                    try {
                        setOpacity(1.0f);
                    } catch (Exception ignored) {
                    }
                    fadeTimer.stop();
                    dismissTimer.start();
                } else {
                    try {
                        setOpacity(opacity);
                    } catch (Exception ignored) {
                    }
                }
            });
            fadeTimer.start();
        });
    }

    private void closeToast() {
        if (isClosing) return;
        isClosing = true;
        if (dismissTimer != null) dismissTimer.stop();

        fadeTimer = new Timer(15, e -> {
            opacity -= 0.12f;
            if (opacity <= 0.05f) {
                fadeTimer.stop();
                ACTIVE_TOASTS.remove(this);
                dispose();
                recalculatePositions();
            } else {
                try {
                    setOpacity(opacity);
                } catch (Exception ignored) {
                }
            }
        });
        fadeTimer.start();
    }

    /**
     * Recalcule la position de tous les toasts actifs pour qu'ils s'empilent proprement
     * juste au-dessus de la barre des tâches de Windows en bas à droite.
     */
    private static synchronized void recalculatePositions() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
            Rectangle bounds = gc.getBounds();
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

            int usableRight = bounds.x + bounds.width - insets.right - PADDING_EDGE;
            int usableBottom = bounds.y + bounds.height - insets.bottom - PADDING_EDGE;

            int index = 0;
            for (NotificationToast toast : ACTIVE_TOASTS) {
                int x = usableRight - TOAST_WIDTH;
                int y = usableBottom - ((index + 1) * TOAST_HEIGHT) - (index * STACK_SPACING);
                toast.setLocation(x, y);
                index++;
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Joue un carillon doux à deux tonalités inspiré de Discord (587Hz puis 880Hz).
     * Pur Java (AudioSystem / SourceDataLine), sans aucun fichier externe requis.
     */
    private static void playDiscordChime() {
        Thread.startVirtualThread(() -> {
            try {
                AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                    line.open(format);
                    line.start();

                    byte[] buffer = new byte[44100 * 2 / 5]; // 0.20 sec au total
                    double freq1 = 587.33; // Ré 5
                    double freq2 = 880.00; // La 5

                    int half = buffer.length / 4;
                    for (int i = 0; i < buffer.length / 2; i++) {
                        double freq = (i < half) ? freq1 : freq2;
                        double t = (double) i / 44100.0;
                        double envelope = Math.sin(Math.PI * (double) i / (buffer.length / 2.0));
                        short val = (short) (Math.sin(2.0 * Math.PI * freq * t) * envelope * 8000.0);
                        buffer[2 * i] = (byte) (val & 0xFF);
                        buffer[2 * i + 1] = (byte) ((val >> 8) & 0xFF);
                    }

                    line.write(buffer, 0, buffer.length);
                    line.drain();
                }
            } catch (Exception ignored) {
                // Fallback son système si audio non disponible
                Toolkit.getDefaultToolkit().beep();
            }
        });
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "…";
    }
}
