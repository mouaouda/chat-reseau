package chatreseau.util;

import chatreseau.ui.ModernButton;
import chatreseau.ui.NotificationToast;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
 * GÃƒÂ¨re la vÃƒÂ©rification automatique des mises ÃƒÂ  jour sur GitHub Releases (via `gh` ou API REST),
 * le tÃƒÂ©lÃƒÂ©chargement du nouveau `.jar` / `.exe` et le redÃƒÂ©marrage automatique en 1 clic.
 */
public class UpdateManager {

    public static final String CURRENT_VERSION = "v1.0.1";
    public static final String GITHUB_REPO = "mouaouda/chat-reseau";

    public record ReleaseInfo(String tagName, String name, String body, String url) {
    }

    /**
     * VÃƒÂ©rification automatique au lancement de l'application.
     * Si une nouvelle version existe, propose ÃƒÂ  l'utilisateur de l'installer en 1 clic.
     */
    public static void checkOnStartup(JFrame parent) {
        Thread.startVirtualThread(() -> {
            ReleaseInfo latest = fetchLatestRelease();
            if (latest != null && latest.tagName() != null) {
                if (compareVersions(latest.tagName(), CURRENT_VERSION) > 0) {
                    SwingUtilities.invokeLater(() -> promptAutoUpdate(parent, latest));
                }
            }
        });
    }

    /**
     * VÃƒÂ©rification manuelle dÃƒÂ©clenchÃƒÂ©e par le bouton "Mises ÃƒÂ  jour GitHub".
     */
    public static void checkForUpdatesAsync(JFrame parent, boolean notifyIfUpToDate) {
        Thread.startVirtualThread(() -> {
            try {
                ReleaseInfo latest = fetchLatestRelease();
                if (latest == null || latest.tagName() == null) {
                    if (notifyIfUpToDate) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                parent,
                                "Aucune release GitHub trouvÃƒÂ©e pour " + GITHUB_REPO + ".\n" +
                                        "Assurez-vous qu'une release existe sur GitHub via `gh release create`.",
                                "Mises ÃƒÂ  jour",
                                JOptionPane.INFORMATION_MESSAGE
                        ));
                    }
                    return;
                }

                boolean isNewer = compareVersions(latest.tagName(), CURRENT_VERSION) > 0;
                SwingUtilities.invokeLater(() -> {
                    if (isNewer) {
                        promptAutoUpdate(parent, latest);
                    } else if (notifyIfUpToDate) {
                        JOptionPane.showMessageDialog(
                                parent,
                                "Votre application est dÃƒÂ©jÃƒÂ  ÃƒÂ  jour !\n" +
                                        "Version installÃƒÂ©e : " + CURRENT_VERSION + "\n" +
                                        "DerniÃƒÂ¨re release sur GitHub : " + latest.tagName(),
                                "Application ÃƒÂ  jour",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                });
            } catch (Exception ex) {
                if (notifyIfUpToDate) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            parent,
                            "Erreur lors de la vÃƒÂ©rification de mise ÃƒÂ  jour :\n" + ex.getMessage(),
                            "Erreur",
                            JOptionPane.WARNING_MESSAGE
                    ));
                }
            }
        });
    }

    /**
     * Affiche une boÃƒÂ®te de dialogue proposant de mettre ÃƒÂ  jour et redÃƒÂ©marrer en 1 clic.
     */
    public static void promptAutoUpdate(JFrame parent, ReleaseInfo release) {
        // Affiche aussi un toast discret
        NotificationToast toast = new NotificationToast(
                "Mise ÃƒÂ  jour disponible !",
                "Ã¢â‚¬Â¢ " + release.tagName(),
                "Nouvelle version prÃƒÂªte ÃƒÂ  ÃƒÂªtre installÃƒÂ©e.",
                () -> promptAutoUpdate(parent, release)
        );
        toast.showNotification();

        JDialog dialog = new JDialog(parent, "Mise ÃƒÂ  jour disponible : " + release.tagName(), true);
        dialog.setSize(530, 410);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(new Color(24, 25, 28));

        // En-tÃƒÂªte
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(new Color(32, 34, 37));
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel titleLbl = new JLabel("Nouvelle version disponible : " + release.tagName());
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLbl.setForeground(Color.WHITE);

        JLabel currentLbl = new JLabel("Version actuelle : " + CURRENT_VERSION + "  Ã¢Å¾â€  DerniÃƒÂ¨re version : " + release.tagName());
        currentLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        currentLbl.setForeground(new Color(96, 165, 250));

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
                : "Nouvelle version disponible avec amÃƒÂ©liorations et correctifs.";
        notesArea.setText("Notes de mise ÃƒÂ  jour (" + release.name() + ") :\n\n" + notesText);

        JScrollPane scrollNotes = new JScrollPane(notesArea);
        scrollNotes.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        scrollNotes.getViewport().setBackground(new Color(24, 25, 28));

        // Barre infÃƒÂ©rieure avec boutons modernes
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        bottomBar.setBackground(new Color(32, 34, 37));

        ModernButton applyNowBtn = new ModernButton("Installer & RedÃƒÂ©marrer", new Color(37, 99, 235));
        applyNowBtn.setPreferredSize(new Dimension(180, 38));
        applyNowBtn.addActionListener(e -> {
            dialog.dispose();
            downloadAndApplyUpdate(parent, release.tagName());
        });

        ModernButton openBrowserBtn = new ModernButton("Voir sur GitHub", new Color(5, 150, 105));
        openBrowserBtn.setPreferredSize(new Dimension(130, 38));
        openBrowserBtn.addActionListener(e -> {
            try {
                if (release.url() != null && !release.url().isEmpty()) {
                    Desktop.getDesktop().browse(URI.create(release.url()));
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Impossible d'ouvrir le navigateur : " + ex.getMessage());
            }
        });

        ModernButton laterBtn = new ModernButton("Plus tard", new Color(71, 85, 105));
        laterBtn.setPreferredSize(new Dimension(90, 38));
        laterBtn.addActionListener(e -> dialog.dispose());

        bottomBar.add(applyNowBtn);
        bottomBar.add(openBrowserBtn);
        bottomBar.add(laterBtn);

        dialog.add(header, BorderLayout.NORTH);
        dialog.add(scrollNotes, BorderLayout.CENTER);
        dialog.add(bottomBar, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    /**
     * TÃƒÂ©lÃƒÂ©charge la mise ÃƒÂ  jour et applique le remplacement avec redÃƒÂ©marrage automatique.
     */
    private static void downloadAndApplyUpdate(JFrame parent, String tag) {
        JDialog progressDialog = new JDialog(parent, "Mise ÃƒÂ  jour en cours...", true);
        progressDialog.setSize(380, 140);
        progressDialog.setLocationRelativeTo(parent);
        progressDialog.setLayout(new BorderLayout());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(16, 20, 16, 20));
        p.setBackground(new Color(24, 25, 28));

        JLabel statusLbl = new JLabel("TÃƒÂ©lÃƒÂ©chargement de la version " + tag + " depuis GitHub...");
        statusLbl.setForeground(Color.WHITE);
        statusLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);

        p.add(statusLbl);
        p.add(Box.createVerticalStrut(14));
        p.add(bar);
        progressDialog.add(p, BorderLayout.CENTER);

        Thread.startVirtualThread(() -> {
            try {
                File tempUpdatedJar = new File(System.getProperty("java.io.tmpdir"), "CHAT_RESEAU_update.jar");
                if (tempUpdatedJar.exists()) {
                    tempUpdatedJar.delete();
                }

                // 1. Essai de tÃƒÂ©lÃƒÂ©chargement avec `gh`
                boolean downloaded = false;
                try {
                    ProcessBuilder pb = new ProcessBuilder("gh", "release", "download", tag, "-R", GITHUB_REPO, "-p", "CHAT_RESEAU.jar", "--output", tempUpdatedJar.getAbsolutePath(), "--clobber");
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();
                    if (proc.waitFor() == 0 && tempUpdatedJar.exists() && tempUpdatedJar.length() > 1000) {
                        downloaded = true;
                    }
                } catch (Exception ignored) {
                }

                // 2. Repli de tÃƒÂ©lÃƒÂ©chargement HTTPS direct si gh n'est pas disponible
                if (!downloaded) {
                    String downloadUrl = "https://github.com/" + GITHUB_REPO + "/releases/download/" + tag + "/CHAT_RESEAU.jar";
                    HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                    HttpRequest req = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
                    HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());

                    if (resp.statusCode() == 200) {
                        try (InputStream in = resp.body(); FileOutputStream out = new FileOutputStream(tempUpdatedJar)) {
                            in.transferTo(out);
                            downloaded = true;
                        }
                    }
                }

                if (!downloaded || !tempUpdatedJar.exists() || tempUpdatedJar.length() < 1000) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        JOptionPane.showMessageDialog(
                                parent,
                                "Impossible de tÃƒÂ©lÃƒÂ©charger le fichier de mise ÃƒÂ  jour depuis GitHub.\n" +
                                        "VÃƒÂ©rifiez votre connexion ou tÃƒÂ©lÃƒÂ©chargez directement depuis GitHub Releases.",
                                "Erreur de tÃƒÂ©lÃƒÂ©chargement",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                    return;
                }

                // 3. DÃƒÂ©terminer l'emplacement cible et le mode d'exÃƒÂ©cution (.exe ou .jar)
                File currentJar = detectCurrentJarPath();
                File appDir = currentJar.getParentFile();
                File parentOfApp = (appDir != null) ? appDir.getParentFile() : null;
                File exeInParent = (parentOfApp != null) ? new File(parentOfApp, "ChatReseau.exe") : null;

                boolean isRunningAsExe = (exeInParent != null && exeInParent.exists());
                File executableToRun = isRunningAsExe ? exeInParent : null;

                if (!isRunningAsExe) {
                    File localExe = new File("dist/ChatReseau/ChatReseau.exe");
                    if (localExe.exists()) {
                        executableToRun = localExe;
                    }
                }

                // CrÃƒÂ©er le script batch temporaire qui attend la fermeture de la JVM, remplace le JAR et relance
                File updaterScript = new File(System.getProperty("java.io.tmpdir"), "apply_chat_update.bat");
                StringBuilder batch = new StringBuilder();
                batch.append("@echo off\r\n");
                batch.append("chcp 65001 >nul\r\n");
                batch.append("timeout /t 1 /nobreak >nul\r\n");

                // Boucle de copie avec rÃƒÂ©essais en cas de verrouillage temporaire Windows
                batch.append("set /a retries=0\r\n");
                batch.append(":retry_copy\r\n");
                batch.append("copy /y \"").append(tempUpdatedJar.getAbsolutePath()).append("\" \"").append(currentJar.getAbsolutePath()).append("\" >nul 2>&1\r\n");
                batch.append("if errorlevel 1 (\r\n");
                batch.append("    set /a retries+=1\r\n");
                batch.append("    if %retries% lss 10 (\r\n");
                batch.append("        timeout /t 1 /nobreak >nul\r\n");
                batch.append("        goto retry_copy\r\n");
                batch.append("    )\r\n");
                batch.append(")\r\n");

                // Si un second jar existe (ex: dist/ChatReseau/app/CHAT_RESEAU.jar en dev), on le synchronise
                File distAppJar = new File("dist/ChatReseau/app/CHAT_RESEAU.jar");
                if (distAppJar.exists() && !distAppJar.getAbsoluteFile().equals(currentJar.getAbsoluteFile())) {
                    batch.append("copy /y \"").append(tempUpdatedJar.getAbsolutePath()).append("\" \"").append(distAppJar.getAbsolutePath()).append("\" >nul 2>&1\r\n");
                }

                // Supprimer le fichier temporaire
                batch.append("del /f /q \"").append(tempUpdatedJar.getAbsolutePath()).append("\" >nul 2>&1\r\n");

                // Relancer l'application (.exe ou .jar)
                if (isRunningAsExe && executableToRun != null) {
                    batch.append("start \"\" \"").append(executableToRun.getAbsolutePath()).append("\"\r\n");
                } else {
                    File javaBin = new File(System.getProperty("java.home"), "bin/javaw.exe");
                    String javaCmd = javaBin.exists() ? "\"" + javaBin.getAbsolutePath() + "\"" : "javaw";
                    batch.append("start \"\" ").append(javaCmd).append(" -jar \"").append(currentJar.getAbsolutePath()).append("\"\r\n");
                }

                // Le script supprime sa propre trace
                batch.append("del \"%~f0\"\r\n");

                try (FileOutputStream fos = new FileOutputStream(updaterScript)) {
                    fos.write(batch.toString().getBytes(StandardCharsets.ISO_8859_1));
                }

                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    try {
                        new ProcessBuilder("cmd.exe", "/c", updaterScript.getAbsolutePath()).start();
                        System.exit(0);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(parent, "Erreur au redÃƒÂ©marrage : " + ex.getMessage());
                    }
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(parent, "Erreur lors de la mise ÃƒÂ  jour : " + ex.getMessage());
                });
            }
        });

        progressDialog.setVisible(true);
    }

    private static File detectCurrentJarPath() {
        try {
            File codeSource = new File(UpdateManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (codeSource.isFile() && codeSource.getName().endsWith(".jar")) {
                return codeSource.getAbsoluteFile();
            }
        } catch (Exception ignored) {
        }
        return new File("CHAT_RESEAU.jar").getAbsoluteFile();
    }

    /**
     * RÃƒÂ©cupÃƒÂ¨re les infos de la derniÃƒÂ¨re release en utilisant d'abord `gh`, puis l'API GitHub en secours.
     */
    public static ReleaseInfo fetchLatestRelease() {
        // 1. Essai avec la commande `gh`
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
