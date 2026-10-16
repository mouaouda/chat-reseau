package chatreseau.ui;

import javafx.scene.control.Button;
import javafx.scene.paint.Color;

/**
 * Bouton moderne JavaFX de style "Material".
 */
public class ModernButton extends Button {

    public ModernButton(String text, Color baseColor) {
        super(text);
        setStyle(
                "-fx-background-color: " + toCss(baseColor) + "; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-background-radius: 8; " +
                "-fx-padding: 8 14 8 14;"
        );
    }

    private static String toCss(Color color) {
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        return String.format("rgb(%d,%d,%d)", r, g, b);
    }
}
