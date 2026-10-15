package edu.course.peerreview.config;

import edu.course.peerreview.service.BusinessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** UTF-8 properties；凭据仅保存到显式指定的本地文件，不打包进 JAR。 */
public final class AppConfig {
    private final Properties values;
    private final Path path;

    public AppConfig(Path path) {
        this.path = path;
        this.values = new Properties();
        values.setProperty("db.url", "jdbc:mysql://localhost:3306/peer_review_assistant?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=15000");
        values.setProperty("db.user", "peer_review");
        values.setProperty("db.password", "");
        values.setProperty("db.trustStore", "");
        values.setProperty("db.trustStorePassword", "");
        values.setProperty("ai.enabled", "false");
        values.setProperty("ai.baseUrl", "https://api.anthropic.com");
        values.setProperty("ai.model", "claude-opus-5-5");
        values.setProperty("ai.timeoutSeconds", "90");
        values.setProperty("ai.apiKey", "");
        values.setProperty("ai.refusalFallback", "auto");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                values.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                throw new BusinessException("配置文件无法读取，请检查路径、权限和 properties 格式。", e);
            }
        }
    }
    public String get(String key) { return values.getProperty(key, ""); }
    public void set(String key, String value) { values.setProperty(key, value); }
    public Path path() { return path; }
    public boolean aiEnabled() { return Boolean.parseBoolean(get("ai.enabled")); }
    public String aiKey() {
        String env = System.getenv("PEER_REVIEW_AI_KEY");
        return env == null || env.isBlank() ? get("ai.apiKey") : env;
    }
    public boolean refusalFallbackEnabled() {
        String mode = get("ai.refusalFallback");
        if (mode.equalsIgnoreCase("auto")) return "api.anthropic.com".equalsIgnoreCase(edu.course.peerreview.ai.AiEndpoint.parse(get("ai.baseUrl")).getHost());
        if (mode.equalsIgnoreCase("true") || mode.equalsIgnoreCase("false")) return Boolean.parseBoolean(mode);
        throw new BusinessException("ai.refusalFallback 须为 auto、true 或 false。");
    }
    public int timeoutSeconds() {
        try {
            int value = Integer.parseInt(get("ai.timeoutSeconds"));
            if (value < 5 || value > 300) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) { throw new BusinessException("AI 超时须为 5—300 秒的整数。"); }
    }
    public void save() {
        try {
            Path parent = path.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, ".peer-review-", ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                    values.store(writer, "Peer Review Assistant - local secrets, do not submit");
                }
                try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { throw new BusinessException("配置保存失败，请检查目录权限。", e); }
    }
}
