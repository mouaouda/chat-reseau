package chatreseau.ui;

import chatreseau.model.PeerUser;
import chatreseau.network.NetworkManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Fenetre principale JavaFX du chat reseau local.
 */
public class ChatWindow {

    private final NetworkManager networkManager;
    private final BorderPane root = new BorderPane();
    private final ObservableList<String> messages = FXCollections.observableArrayList();
    private final ObservableList<PeerUser> peers = FXCollections.observableArrayList();
    private final ListView<String> messagesView = new ListView<>(messages);
    private final ListView<PeerUser> peersView = new ListView<>(peers);

    public ChatWindow(NetworkManager networkManager) {
        this.networkManager = networkManager;
        initUI();
        registerListeners();
    }

    public Node getRoot() {
        return root;
    }

    private void initUI() {
        peersView.setPrefWidth(260);
        peersView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PeerUser item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getUsername() + " (" + item.getAddress().getHostAddress() + ")");
                }
            }
        });

        VBox sidebar = new VBox(12);
        sidebar.setPadding(new Insets(12));
        Label peersLabel = new Label("Participants");
        peersLabel.setStyle("-fx-font-weight: bold;");
        sidebar.getChildren().addAll(peersLabel, peersView);

        VBox center = new VBox(12, messagesView);
        center.setPadding(new Insets(12));
        VBox.setVgrow(messagesView, Priority.ALWAYS);

        TextField input = new TextField();
        input.setPromptText("Ecrire un message au salon general...");
        Button sendButton = new Button("Envoyer");
        sendButton.setDefaultButton(true);

        HBox inputBox = new HBox(8, input, sendButton);
        inputBox.setPadding(new Insets(12));
        HBox.setHgrow(input, Priority.ALWAYS);
        inputBox.setAlignment(Pos.CENTER_LEFT);

        sendButton.setOnAction(e -> {
            String text = input.getText();
            if (text != null && !text.isBlank()) {
                networkManager.sendGeneralMessage(text.trim());
                input.clear();
            }
        });

        ToolBar toolbar = new ToolBar();
        Button updateButton = new Button("Verifier les mises a jour");
        updateButton.setOnAction(e -> chatreseau.util.UpdateManager.checkForUpdatesAsync(null, true));
        toolbar.getItems().add(updateButton);

        root.setTop(toolbar);
        root.setLeft(sidebar);
        root.setCenter(center);
        root.setBottom(inputBox);
    }

    private void registerListeners() {
        networkManager.addListener(new NetworkManager.NetworkListener() {
            @Override
            public void onPeersUpdated(Collection<chatreseau.model.PeerUser> peersCollection) {
                Platform.runLater(() -> peers.setAll(new ArrayList<>(peersCollection)));
            }

            @Override
            public void onMessageReceived(chatreseau.model.ChatMessage message) {
                Platform.runLater(() -> {
                    String sender = message.isFromMe() ? "Moi" : message.getSenderName();
                    String text = "[" + message.getTimestamp() + "] " + sender + " : " + message.getContent();
                    messages.add(text);
                    messagesView.scrollTo(messages.size() - 1);
                });
            }

            @Override
            public void onPeerJoined(chatreseau.model.PeerUser peer) {
                Platform.runLater(() -> {
                    if (peers.stream().noneMatch(p -> p.getId().equals(peer.getId()))) {
                        peers.add(peer);
                    }
                });
            }

            @Override
            public void onPeerLeft(chatreseau.model.PeerUser peer) {
                Platform.runLater(() -> peers.removeIf(p -> p.getId().equals(peer.getId())));
            }

            @Override
            public void onTypingReceived(String senderId, String senderName, String targetId) {
                // A completer eventuellement pour un indicateur de frappe.
            }

            @Override
            public void onNameConflictDetected(String conflictingName) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING,
                            "Conflit de nom detecte : " + conflictingName + "\nVeuillez choisir un autre pseudo.",
                            ButtonType.OK);
                    alert.showAndWait();
                });
            }
        });
    }
}
