package edu.course.peerreview;

import edu.course.peerreview.ai.AiServices;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.dao.Database;
import edu.course.peerreview.ui.*;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

public final class App {
    private App() { }
    private static Path defaultConfigPath() {
        try {
            Path location = Path.of(App.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (location.toString().endsWith(".jar")) {
                Path parent = location.getParent();
                if (parent.getFileName().toString().equals("bin")) parent = parent.getParent();
                return parent.resolve("config/application.properties");
            }
        } catch (java.net.URISyntaxException | RuntimeException ignored) { }
        return Path.of("config", "application.properties");
    }
    public static void main(String[] args) {
        Path configPath = defaultConfigPath();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--help")) {
                System.out.println("Usage: java -jar peer-review-assistant.jar [--config <local.properties>]\nJava 17+, Swing desktop, MySQL 8.x. No network request is made until an explicit user action."); return;
            }
            if (args[i].equals("--version")) { System.out.println("Peer Review Assistant 1.1.0"); return; }
            if (args[i].equals("--config") && i + 1 < args.length) configPath = Path.of(args[++i]);
            else { System.err.println("Unknown argument or missing value. Use --help."); return; }
        }
        Path selected = configPath;
        SwingUtilities.invokeLater(() -> {
            Ui.theme();
            Ui.safely(() -> {
                AppConfig config = new AppConfig(selected);
                new LoginFrame(config, new Database(config), AiServices.provider(config)).setVisible(true);
            });
        });
    }
}
