package edu.course.peerreview.config;

import edu.course.peerreview.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Separate private DB/API presets; selecting a preset never activates it without a successful check. */
public final class ConfigProfiles {
    public record Profile(Path path, String name) { @Override public String toString() { return name; } }
    private final Path directory;
    public ConfigProfiles(AppConfig config) { directory = config.path().toAbsolutePath().getParent().resolve("profiles"); }
    public List<Profile> list(String group) {
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.list(directory)) {
            List<Profile> result = new ArrayList<>();
            for (Path p : stream.filter(p -> p.getFileName().toString().startsWith(group + "-") && p.toString().endsWith(".local.properties")).sorted().toList()) {
                Properties values = load(p); result.add(new Profile(p, values.getProperty("profile.name", "未命名方案")));
            }
            return result;
        } catch (IOException e) { throw new BusinessException("无法读取连接方案目录。"); }
    }
    public AppConfig select(AppConfig current, String group, Profile profile) {
        AppConfig candidate = current.copy(); Properties stored = load(profile.path());
        for (String key : stored.stringPropertyNames()) if (belongs(key, group)) candidate.set(key, stored.getProperty(key));
        return candidate;
    }
    public void save(String group, String name, AppConfig candidate) {
        String clean = Validation.text(name, "方案名称", 60); Properties stored = new Properties(); stored.setProperty("profile.name", clean);
        for (String key : candidate.keys()) if (belongs(key, group)) stored.setProperty(key, candidate.get(key));
        try {
            Files.createDirectories(directory); Path path = directory.resolve(group + "-" + UUID.randomUUID() + ".local.properties");
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { stored.store(writer, "Private connection profile - do not distribute"); }
        } catch (IOException e) { throw new BusinessException("方案副本无法保存，本次不切换，请检查目录权限。"); }
    }
    public static boolean belongs(String key, String group) { return key.startsWith(group + ".") || key.equals("course.demo." + group); }
    private Properties load(Path path) {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { values.load(reader); return values; }
        catch (IOException | IllegalArgumentException e) { throw new BusinessException("连接方案无法读取，请检查文件格式与权限。"); }
    }
}
