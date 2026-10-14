package chatreseau.util;

import chatreseau.ui.ModernButton;
import chatreseau.ui.NotificationToast;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestionnaire de mise à jour automatique via GitHub Releases en utilisant `gh` (GitHub CLI)
 * avec repli automatique sur l'API REST GitHub.
 */
public class UpdateManager {

    public static final String CURRENT_VERSION = "v1.0.0";
    public static final String GITHUB_REPO = "mouaouda/chat-reseau";

    public record ReleaseInfo(String tagName, String name, String body, String url) {
    }

    /**
     * Vérifie la présence d'une nouvelle version sur GitHub.
     * Exécuté en tâche de fond.
     */
    public static void checkForUpdatesAsync(JFrame parent, boolean notifyIfUpToDate) {
        Thread.startVirtualThread(() -> {
            try {
                ReleaseInfo latest = fetchLatestRelease();
                if (latest == null || latest.tagName() == null) {
                    if (notifyIfUpToDate) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                parent,
                                "Aucune release GitHub trouvée pour " + GITHUB_REPO + ".\n" +
                                        "Assurez-vous qu'une release existe sur GitHub via `gh release create`.",
                                "Mises à jour",
                                JOptionPane.INFORMATION_MESSAGE
                        ));
                    }
                    return;
                }

                boolean isNewer = compareVersions(latest.tagName(), CURRENT_VERSION) > 0;
                SwingUtilities.invokeLater(() -> {
                    if (isNewer) {
                        // Toast Discord de mise à jour
                        NotificationToast toast = new NotificationToast(
                                "Mise à jour disponible !",
                                "• " + latest.tagName(),
                                "Nouvelle version disponible sur GitHub. Cliquez pour mettre à jour.",
                                () -> showUpdateDialog(parent, latest)
                        );
                        toast.showNotification();

                        // Afficher la boîte de dialogue si demandé manuellement
                        if (notifyIfUpToDate) {
                            showUpdateDialog(parent, latest);
                        }
                    } else if (notifyIfUpToDate) {
                        JOptionPane.showMessageDialog(
                                parent,
                                "Votre application est à jour !\nVersion actuelle : " + CURRENT_VERSION + "\nDernière release GitHub : " + latest.tagName(),
                                "À jour",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                });
            } catch (Exception ex) {
                if (notifyIfUpToDate) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            parent,
                            "Erreur lors de la vérification de mise à jour :\n" + ex.getMessage(),
                            "Erreur de mise à jour",
                            JOptionPane.WARNING_MESSAGE
                    ));
                }
            }
        });
    }

    /**
     * Récupère la dernière release en utilisant d'abord la commande `gh`, puis l'API GitHub en secours.
     */
    public static ReleaseInfo fetchLatestRelease() {
        // 1. Essai prioritaire avec la commande `gh`
        try {
            ProcessBuilder pb = new ProcessBuilder("gh", "release", "view", "-R", GITHUB_REPO, "--json", "tagName,name,body,url");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            int code = proc.waitFor();
            if (code == 0 && sb.toString().contains("tagName")) {
                String json = sb.toString();
                String tagName = extractJsonField(json, "tagName");
                String name = extractJsonField(json, "name");
                String body = extractJsonField(json, "body");
                String url = extractJsonField(json, "url");
                if (tagName != null && !tagName.isBlank()) {
                    return new ReleaseInfo(tagName, name, body, url);
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Repli avec l'API REST HTTPS GitHub
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest"))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Chat-Reseau-App")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                String json = response.body();
                String tagName = extractJsonField(json, "tag_name");
                String name = extractJsonField(json, "name");
                String body = extractJsonField(json, "body");
                String url = extractJsonField(json, "html_url");
                if (tagName != null && !tagName.isBlank()) {
                    return new ReleaseInfo(tagName, name, body, url);
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Boîte de dialogue affichant la nouvelle version disponible avec téléchargement via `gh`.
     */
    public static void showUpdateDialog(JFrame parent, ReleaseInfo release) {
        JDialog dialog = new JDialog(parent, "Mise à jour disponible : " + release.tagName(), true);
        dialog.setSize(520, 380);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(24, 25, 28));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(new Color(32, 34, 37));
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel titleLbl = new JLabel("🚀 Nouvelle version disponible : " + release.tagName());
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLbl.setForeground(Color.WHITE);

        JLabel currentLbl = new JLabel("Version actuelle : " + CURRENT_VERSION + "  ➔  Dernière version : " + release.tagName());
        currentLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        currentLbl.setForeground(new Color(88, 101, 242));

        header.add(titleLbl);
        header.add(Box.createVerticalStrut(4));
        header.add(currentLbl);

        // Notes de release
        JTextArea notesArea = new JTextArea();
        notesArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        notesArea.setEditable(false);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setBackground(new Color(43, 45, 49));
        notesArea.setForeground(new Color(220, 221, 222));
        notesArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        String notesText = (release.body() != null && !release.body().isBlank())
                ? release.body()
                : "Aucune note de release fournie pour cette version.";
        notesArea.setText("Notes de mise à jour (" + release.name() + ") :\n\n" + notesText);

        JScrollPane scrollNotes = new JScrollPane(notesArea);
        scrollNotes.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        scrollNotes.getViewport().setBackground(new Color(24, 25, 28));

        // Boutons d'action
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        bottomBar.setBackground(new Color(32, 34, 37));

        ModernButton updateWithGhBtn = new ModernButton("Telecharger via gh", new Color(88, 101, 242));
        updateWithGhBtn.setPreferredSize(new Dimension(170, 36));
        updateWithGhBtn.addActionListener(e -> {
            Thread.startVirtualThread(() -> runGhDownload(dialog, release.tagName()));
        });

        ModernButton openBrowserBtn = new ModernButton("Voir sur GitHub", new Color(5, 150, 105));
        openBrowserBtn.setPreferredSize(new Dimension(140, 36));
        openBrowserBtn.addActionListener(e -> {
            try {
                if (release.url() != null && !release.url().isEmpty()) {
                    Desktop.getDesktop().browse(URI.create(release.url()));
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Impossible d'ouvrir le navigateur : " + ex.getMessage());
            }
        });

        ModernButton closeBtn = new ModernButton("Fermer", new Color(71, 85, 105));
        closeBtn.setPreferredSize(new Dimension(90, 36));
        closeBtn.addActionListener(e -> dialog.dispose());

        bottomBar.add(updateWithGhBtn);
        bottomBar.add(openBrowserBtn);
        bottomBar.add(closeBtn);

        dialog.add(header, BorderLayout.NORTH);
        dialog.add(scrollNotes, BorderLayout.CENTER);
        dialog.add(bottomBar, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private static void runGhDownload(JDialog parentDialog, String tag) {
        try {
            ProcessBuilder pb = new ProcessBuilder("gh", "release", "download", tag, "-R", GITHUB_REPO, "--clobber");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            if (code == 0) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            parentDialog,
                            "Mise à jour " + tag + " téléchargée avec succès avec `gh` !\n" +
                                    "Les fichiers ont été placés dans le dossier de l'application.",
                            "Téléchargement réussi",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            parentDialog,
                            "Aucun asset binaire n'est attaché à la release " + tag + ".\n" +
                                    "Vous pouvez consulter la page de la release sur GitHub.",
                            "Information",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                });
            }
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    parentDialog,
                    "Erreur lors de l'exécution de gh release download : " + ex.getMessage(),
                    "Erreur",
                    JOptionPane.ERROR_MESSAGE
            ));
        }
    }

    /**
     * Compare deux versions au format "v1.2.3". Retourne > 0 si v1 > v2.
     */
    public static int compareVersions(String v1, String v2) {
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String clean1 = v1.replaceAll("[^0-9.]", "");
        String clean2 = v2.replaceAll("[^0-9.]", "");

        String[] parts1 = clean1.split("\\.");
        String[] parts2 = clean2.split("\\.");

        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int num1 = (i < parts1.length && !parts1[i].isEmpty()) ? Integer.parseInt(parts1[i]) : 0;
            int num2 = (i < parts2.length && !parts2[i].isEmpty()) ? Integer.parseInt(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\n", "\n").replace("\\r", "").replace("\\\"", "\"");
        }
        return "";
    }
}
