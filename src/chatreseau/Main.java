package chatreseau;

import chatreseau.network.NetworkManager;
import chatreseau.ui.ChatWindow;
import chatreseau.ui.LoginDialog;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Point d'entrée principal de l'application Chat Réseau Local.
 */
public class Main {

    private static final AtomicInteger OPEN_WINDOWS = new AtomicInteger(0);

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(Main::launchNewClientInstance);
    }

    /**
     * Lance une instance complète du client (Inscription avec le Nom -> Vérification -> Fenêtre de Chat).
     * Permet également d'ouvrir plusieurs fenêtres sur le même PC pour tester le réseau local.
     */
    public static void launchNewClientInstance() {
        NetworkManager networkManager = new NetworkManager();
        try {
            networkManager.startNetworkServices();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    null,
                    "Impossible de démarrer les services réseau local : " + e.getMessage(),
                    "Erreur Réseau",
                    JOptionPane.ERROR_MESSAGE
            );
            if (OPEN_WINDOWS.get() == 0) {
                System.exit(1);
            }
            return;
        }

        LoginDialog loginDialog = new LoginDialog(null, networkManager, null);
        String chosenIdentity = loginDialog.showDialog();

        if (chosenIdentity == null || chosenIdentity.isBlank()) {
            networkManager.shutdown();
            if (OPEN_WINDOWS.get() == 0) {
                System.exit(0);
            }
            return;
        }

        OPEN_WINDOWS.incrementAndGet();
        ChatWindow chatWindow = new ChatWindow(networkManager);
        chatWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (OPEN_WINDOWS.decrementAndGet() <= 0) {
                    System.exit(0);
                }
            }
        });
        chatWindow.setVisible(true);
    }
}
