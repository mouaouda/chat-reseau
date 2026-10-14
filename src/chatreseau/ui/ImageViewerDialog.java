package chatreseau.ui;

import chatreseau.util.ImageUtil;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Visionneuse d'image avec possibilité de sauvegarder l'image sur le disque.
 */
public class ImageViewerDialog extends JDialog {

    private static final Color BG_DARK = new Color(24, 25, 28);
    private static final Color DISCORD_BLURPLE = new Color(88, 101, 242);
    private static final Color BTN_SAVE = new Color(5, 150, 105); // Vert émeraude
    private static final Color BTN_CLOSE = new Color(71, 85, 105); // Ardoise

    public ImageViewerDialog(JFrame parent, String senderName, byte[] imageData, String caption) {
        super(parent, "Image de " + senderName, true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        BufferedImage img = ImageUtil.bytesToImage(imageData);
        if (img == null) {
            JOptionPane.showMessageDialog(parent, "Impossible d'afficher cette image.", "Erreur", JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }

        // Image centrée dans un JScrollPane
        JLabel imageLabel = new JLabel(new ImageIcon(img));
        imageLabel.setHorizontalAlignment(JLabel.CENTER);

        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.setBackground(BG_DARK);
        imagePanel.add(imageLabel, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane(imagePanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_DARK);

        // Barre inférieure avec caption et boutons stylés
        JPanel bottomBar = new JPanel(new BorderLayout(12, 0));
        bottomBar.setBackground(new Color(32, 34, 37));
        bottomBar.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel captionLabel = new JLabel((caption != null && !caption.isBlank()) ? caption : ("Photo partagée par " + senderName));
        captionLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        captionLabel.setForeground(Color.WHITE);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonsPanel.setOpaque(false);

        JButton saveBtn = new JButton("💾 Enregistrer l'image");
        saveBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        saveBtn.setBackground(BTN_SAVE);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.setBorder(new EmptyBorder(8, 16, 8, 16));
        saveBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveBtn.addActionListener(e -> saveImage(img, senderName));

        JButton closeBtn = new JButton("Fermer");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeBtn.setBackground(BTN_CLOSE);
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(new EmptyBorder(8, 16, 8, 16));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());

        buttonsPanel.add(saveBtn);
        buttonsPanel.add(closeBtn);

        bottomBar.add(captionLabel, BorderLayout.WEST);
        bottomBar.add(buttonsPanel, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(bottomBar, BorderLayout.SOUTH);

        // Dimensions adaptées à l'image
        int targetW = Math.min(Math.max(img.getWidth() + 60, 520), 1000);
        int targetH = Math.min(Math.max(img.getHeight() + 110, 420), 750);
        setSize(targetW, targetH);
        setLocationRelativeTo(parent);
    }

    private void saveImage(BufferedImage img, String sender) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Enregistrer l'image reçue");
        chooser.setSelectedFile(new File("image_" + sender + "_" + System.currentTimeMillis() + ".png"));
        chooser.setFileFilter(new FileNameExtensionFilter("Images PNG (*.png)", "png"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith(".png") && !target.getName().toLowerCase().endsWith(".jpg")) {
                target = new File(target.getAbsolutePath() + ".png");
            }
            try {
                ImageIO.write(img, "png", target);
                JOptionPane.showMessageDialog(this, "Image enregistrée avec succès sous :\n" + target.getAbsolutePath(), "Enregistrement réussi", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors de l'enregistrement : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
