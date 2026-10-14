package chatreseau.util;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

/**
 * Utilitaires pour le redimensionnement, la compression et l'affichage d'images dans le chat.
 */
public class ImageUtil {

    private static final int MAX_SEND_DIMENSION = 1200; // Largeur/Hauteur max avant envoi
    private static final int THUMBNAIL_MAX_WIDTH = 320;
    private static final int THUMBNAIL_MAX_HEIGHT = 220;

    /**
     * Charge une image depuis un fichier, la redimensionne si nécessaire et la compresse en PNG/JPEG.
     */
    public static byte[] prepareImageForSending(File file) throws IOException {
        BufferedImage original = ImageIO.read(file);
        if (original == null) {
            throw new IOException("Format d'image non reconnu ou fichier corrompu.");
        }
        return processAndCompress(original);
    }

    /**
     * Traite et compresse une BufferedImage (provenant par exemple du presse-papier).
     */
    public static byte[] processAndCompress(BufferedImage original) throws IOException {
        int width = original.getWidth();
        int height = original.getHeight();

        BufferedImage toSend = original;
        if (width > MAX_SEND_DIMENSION || height > MAX_SEND_DIMENSION) {
            double ratio = Math.min((double) MAX_SEND_DIMENSION / width, (double) MAX_SEND_DIMENSION / height);
            int targetW = Math.max(1, (int) (width * ratio));
            int targetH = Math.max(1, (int) (height * ratio));
            toSend = scaleImage(original, targetW, targetH);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Sauvegarde en JPG ou PNG selon la transparence
        boolean hasAlpha = toSend.getColorModel().hasAlpha();
        String format = hasAlpha ? "png" : "jpg";
        ImageIO.write(toSend, format, baos);
        return baos.toByteArray();
    }

    /**
     * Crée une miniature (thumbnail) prête à afficher dans une bulle de chat.
     */
    public static BufferedImage createThumbnail(byte[] imageData) {
        if (imageData == null || imageData.length == 0) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageData));
            if (img == null) return null;

            int width = img.getWidth();
            int height = img.getHeight();

            double ratio = Math.min((double) THUMBNAIL_MAX_WIDTH / width, (double) THUMBNAIL_MAX_HEIGHT / height);
            int targetW = Math.max(1, (int) (width * ratio));
            int targetH = Math.max(1, (int) (height * ratio));

            return scaleImage(img, targetW, targetH);
        } catch (IOException e) {
            return null;
        }
    }

    public static BufferedImage bytesToImage(byte[] imageData) {
        if (imageData == null || imageData.length == 0) return null;
        try {
            return ImageIO.read(new ByteArrayInputStream(imageData));
        } catch (IOException e) {
            return null;
        }
    }

    public static String encodeBase64(byte[] data) {
        if (data == null) return "";
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] decodeBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BufferedImage scaleImage(BufferedImage src, int w, int h) {
        int type = (src.getTransparency() == BufferedImage.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage resized = new BufferedImage(w, h, type);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return resized;
    }
}
