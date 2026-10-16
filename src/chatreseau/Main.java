package chatreseau;

import chatreseau.network.NetworkManager;
import chatreseau.ui.ChatWindow;
import chatreseau.ui.LoginDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Point d'entree JavaFX principal de l'application Chat Reseau Local.
 * Tout le client est maintenant construit sur JavaFX / Java 25.
 */
public class Main extends Application {

    private NetworkManager networkManager;

    @Override
    public void start(Stage primaryStage) {
        networkManager = new NetworkManager();

        try {
            networkManager.startNetworkServices();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Impossible de demarrer les services reseau local : " + e.getMessage(),
                    ButtonType.OK);
            alert.showAndWait();
            Platform.exit();
            return;
        }

        String chosenIdentity = LoginDialog.show(primaryStage, networkManager, null);
        if (chosenIdentity == null || chosenIdentity.isBlank()) {
            networkManager.shutdown();
            Platform.exit();
            return;
        }

        ChatWindow chatWindow = new ChatWindow(networkManager);
        Scene scene = new Scene(chatWindow.getRoot(), 1100, 720);
        primaryStage.setTitle("Chat Reseau Local");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (networkManager != null) {
            networkManager.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
