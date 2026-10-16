package chatreseau.util;

import chatreseau.ui.NotificationToast;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestionnaire de mises a jour adapte a JavaFX / Java 25.
 */
public final class UpdateManager {

    public static final String CURRENT_VERSION = "v1.0.1";
    public static final String GITHUB_REPO = "mouaouda/chat-reseau";

    public record ReleaseInfo(String tagName, String name, String body, String url) {
    }

    private UpdateManager() {
    }

    public static void checkOnStartup(Window parent) {
        Thread.startVirtualThread(() -> {
            ReleaseInfo latest = fetchLatestRelease();
            if (latest != null && latest.tagName() != null && compareVersions(latest.tagName(), CURRENT_VERSION) > 0) {
                Platform.runLater(() -> promptAutoUpdate(parent, latest));
            }
        });
    }

    public static void checkForUpdatesAsync(Window parent, boolean notifyIfUpToDate) {
        Thread.startVirtualThread(() -> {
            try {
                ReleaseInfo latest = fetchLatestRelease();
                if (latest == null || latest.tagName() == null) {
                    if (notifyIfUpToDate) {
                        Platform.runLater(() -> showInfo(parent, "Aucune release GitHub trouvee pour " + GITHUB_REPO + "."));
                    }
                    return;
                }

                boolean isNewer = compareVersions(latest.tagName(), CURRENT_VERSION) > 0;
                Platform.runLater(() -> {
                    if (isNewer) {
                        promptAutoUpdate(parent, latest);
                    } else if (notifyIfUpToDate) {
                        showInfo(parent, "Votre application est deja a jour !\nVersion installee : " + CURRENT_VERSION + "\nDerniere release : " + latest.tagName());
                    }
                });
            } catch (Exception ex) {
                if (notifyIfUpToDate) {
                    Platform.runLater(() -> showWarning(parent, "Erreur lors de la verification de mise a jour :\n" + ex.getMessage()));
                }
            }
        });
    }

    private static void promptAutoUpdate(Window parent, ReleaseInfo release) {
        NotificationToast.show("Mise a jour disponible !", "• " + release.tagName(), "Nouvelle version prete a etre installee.");

        Platform.runLater(() -> {
            Alert dialog = new Alert(Alert.AlertType.CONFIRMATION,
                    "Nouvelle version " + release.tagName() + " disponible. Voulez-vous la telecharger ?",
                    ButtonType.YES, ButtonType.NO);
            dialog.initOwner(parent);
            dialog.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    Thread.startVirtualThread(() -> downloadAndApplyUpdate(parent, release.tagName()));
                }
            });
        });
    }

    private static void downloadAndApplyUpdate(Window parent, String tag) {
        try {
            File tempUpdatedJar = new File(System.getProperty("java.io.tmpdir"), "CHAT_RESEAU_update.jar");
            if (tempUpdatedJar.exists()) tempUpdatedJar.delete();

            boolean downloaded = false;
            try {
                ProcessBuilder pb = new ProcessBuilder("gh", "release", "download", tag, "-R", GITHUB_REPO, "--pattern", "CHAT_RESEAU.jar", "--clobber");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int exit = p.waitFor();
                if (exit == 0) {
                    File localFile = new File("CHAT_RESEAU.jar");
                    if (localFile.exists() && localFile.length() > 1000) {
                        Files.copy(localFile.toPath(), tempUpdatedJar.toPath());
                        downloaded = true;
                    }
                }
            } catch (Exception ignored) {
            }

            if (!downloaded) {
                String url = "https://github.com/" + GITHUB_REPO + "/releases/download/" + tag + "/CHAT_RESEAU.jar";
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    try (InputStream in = response.body(); FileOutputStream out = new FileOutputStream(tempUpdatedJar)) {
                        in.transferTo(out);
                        downloaded = true;
                    }
                }
            }

            if (!downloaded || !tempUpdatedJar.exists() || tempUpdatedJar.length() < 1000) {
                Platform.runLater(() -> showError(parent, "Impossible de telecharger la mise a jour depuis GitHub."));
                return;
            }

            File currentJar = detectCurrentJarPath();
            Files.copy(tempUpdatedJar.toPath(), currentJar.toPath());

            Platform.runLater(() -> {
                try {
                    Runtime.getRuntime().exec(new String[] {"java", "-jar", currentJar.getAbsolutePath()});
                    System.exit(0);
                } catch (Exception ex) {
                    showError(parent, "Erreur au redemarrage : " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            Platform.runLater(() -> showError(parent, "Erreur lors de la mise a jour : " + ex.getMessage()));
        }
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

    public static int compareVersions(String v1, String v2) {
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String clean1 = v1.replaceAll("[^0-9.]", "");
        String clean2 = v2.replaceAll("[^0-9.]", "");
        String[] p1 = clean1.split("\\.");
        String[] p2 = clean2.split("\\.");
        int limit = Math.max(p1.length, p2.length);

        for (int i = 0; i < limit; i++) {
            int num1 = (i < p1.length && !p1[i].isEmpty()) ? Integer.parseInt(p1[i]) : 0;
            int num2 = (i < p2.length && !p2[i].isEmpty()) ? Integer.parseInt(p2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }

    public static ReleaseInfo fetchLatestRelease() {
        try {
            ProcessBuilder pb = new ProcessBuilder("gh", "release", "view", "-R", GITHUB_REPO, "--json", "tagName,name,body,url");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder json = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line).append("\n");
                }
            }
            if (p.waitFor() == 0 && json.toString().contains("tagName")) {
                String tag = extractJsonField(json.toString(), "tagName");
                if (tag != null && !tag.isBlank()) {
                    return new ReleaseInfo(tag, extractJsonField(json.toString(), "name"), extractJsonField(json.toString(), "body"), extractJsonField(json.toString(), "url"));
                }
            }
        } catch (Exception ignored) {
        }

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest"))
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Chat-Reseau-App")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                String json = response.body();
                String tag = extractJsonField(json, "tag_name");
                if (tag != null && !tag.isBlank()) {
                    return new ReleaseInfo(tag, extractJsonField(json, "name"), extractJsonField(json, "body"), extractJsonField(json, "html_url"));
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\n", "\n").replace("\\r", "").replace("\\\"", "\"");
        }
        return "";
    }

    private static void showInfo(Window parent, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        if (parent != null) alert.initOwner(parent);
        alert.showAndWait();
    }

    private static void showWarning(Window parent, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        if (parent != null) alert.initOwner(parent);
        alert.showAndWait();
    }

    private static void showError(Window parent, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (parent != null) alert.initOwner(parent);
        alert.showAndWait();
    }
}
