package chatreseau.ui;

import chatreseau.network.NetworkManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialogue d'inscription JavaFX moderne pour Java 25.
 */
public final class LoginDialog {

    private LoginDialog() {
    }

    public static String show(Stage owner, NetworkManager networkManager, String initialConflictName) {
        final String[] result = {null};

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Inscription - Chat Reseau Local");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(18));
        grid.setHgap(10);
        grid.setVgap(10);

        Label title = new Label("Inscription");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label instruction = new Label("Entrez votre nom pour rejoindre le reseau local :");
        TextField nameField = new TextField();
        nameField.setPrefWidth(320);
        if (initialConflictName != null && !initialConflictName.isBlank()) {
            nameField.setText(initialConflictName);
        }

        Label status = new Label(" ");
        status.setStyle("-fx-text-fill: #b91c1c;");

        Button joinButton = new Button("Rejoindre le chat");
        Button cancelButton = new Button("Annuler");
        HBox actions = new HBox(8, joinButton, cancelButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        joinButton.setOnAction(e -> {
            String candidate = nameField.getText();
            if (candidate == null || candidate.isBlank()) {
                status.setText("Le nom ne peut pas etre vide.");
                return;
            }

            String trimmed = candidate.trim();
            boolean available = networkManager.checkNameAvailability(trimmed);
            if (!available) {
                status.setText("Ce nom est deja utilise sur le reseau. Choisissez un autre pseudo.");
                return;
            }

            networkManager.completeRegistration(trimmed);
            result[0] = trimmed;
            dialog.close();
        });

        cancelButton.setOnAction(e -> dialog.close());

        grid.add(title, 0, 0);
        grid.add(instruction, 0, 1);
        grid.add(nameField, 0, 2);
        grid.add(status, 0, 3);
        grid.add(actions, 0, 4);

        dialog.setScene(new Scene(grid, 420, 240));
        dialog.showAndWait();
        return result[0];
    }
}
