package chatreseau.ui;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Bouton personnalisé moderne pour Java Swing.
 * Contourne complètement le bug de Windows LookAndFeel qui force les JButton en blanc.
 * Garantit des couleurs vives, un texte blanc parfaitement lisible, des coins arrondis et un effet de survol.
 */
public class ModernButton extends JButton {

    private final Color normalBg;
    private final Color hoverBg;
    private final Color pressedBg;
    private final Color textColor;
    private int cornerRadius = 10;
    private boolean isHovered = false;
    private boolean isPressed = false;

    public ModernButton(String text, Color normalBg, Color hoverBg, Color pressedBg, Color textColor) {
        super(text);
        this.normalBg = normalBg;
        this.hoverBg = hoverBg;
        this.pressedBg = pressedBg;
        this.textColor = textColor != null ? textColor : Color.WHITE;

        setFont(new Font("Segoe UI", Font.BOLD, 13));
        setForeground(this.textColor);

        // Désactive le rendu natif Windows pour empêcher le blanchiment
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    /**
     * Variante avec calcul automatique des couleurs de survol et de clic.
     */
    public ModernButton(String text, Color baseColor) {
        this(text, baseColor, darken(baseColor, 0.85f), darken(baseColor, 0.70f), Color.WHITE);
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Déterminer la couleur de fond
        Color currentBg;
        if (!isEnabled()) {
            currentBg = new Color(100, 116, 139); // Ardoise atténuée
        } else if (isPressed) {
            currentBg = pressedBg;
        } else if (isHovered) {
            currentBg = hoverBg;
        } else {
            currentBg = normalBg;
        }

        // 2. Dessiner le fond arrondi
        g2.setColor(currentBg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);

        // 3. Dessiner le texte centré
        g2.setColor(isEnabled() ? textColor : new Color(203, 213, 225));
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String text = getText();
        if (text != null && !text.isEmpty()) {
            int textX = (getWidth() - fm.stringWidth(text)) / 2;
            int textY = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(text, textX, textY);
        }

        g2.dispose();
    }

    private static Color darken(Color c, float factor) {
        int r = Math.max(0, Math.min(255, (int) (c.getRed() * factor)));
        int g = Math.max(0, Math.min(255, (int) (c.getGreen() * factor)));
        int b = Math.max(0, Math.min(255, (int) (c.getBlue() * factor)));
        return new Color(r, g, b, c.getAlpha());
    }
}
