package chatreseau.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Notification toast JavaFX moderne pour avertir l'utilisateur.
 */
public final class NotificationToast {

    private NotificationToast() {
    }

    public static void show(String title, String subtitle, String message) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: #a3b3c7;");

        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: #e5eef5;");

        Button closeButton = new Button("x");
        closeButton.setOnAction(e -> stage.close());
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_RIGHT);
        header.getChildren().add(closeButton);

        VBox root = new VBox(8, titleLabel, subtitleLabel, messageLabel);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: rgba(17,24,39,0.95); -fx-background-radius: 12; -fx-border-color: rgba(148,163,184,0.4); -fx-border-radius: 12;");

        Scene scene = new Scene(root, 320, 110);
        scene.setFill(null);
        stage.setScene(scene);
        stage.show();

        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.seconds(3.5), root);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setDelay(javafx.util.Duration.seconds(2.5));
        ft.setOnFinished(e -> stage.close());
        ft.play();
    }
}
