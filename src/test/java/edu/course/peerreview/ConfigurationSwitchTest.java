package edu.course.peerreview;

import edu.course.peerreview.config.*;
import edu.course.peerreview.service.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationSwitchTest {
    @TempDir Path directory;
    @Test void draftsAndFailedSaveDoNotReplaceActiveSettings() throws Exception {
        AppConfig current = new AppConfig(directory.resolve("blocked/app.properties")); current.set("db.user", "original");
        AppConfig draft = current.copy(); draft.set("db.user", "new-user");
        assertEquals("original", current.get("db.user")); Files.writeString(directory.resolve("blocked"), "not a directory");
        assertThrows(BusinessException.class, () -> current.applyAndSave(draft)); assertEquals("original", current.get("db.user"));
    }
    @Test void independentProfilesDoNotCopyOtherServiceCredentials() throws Exception {
        AppConfig active = new AppConfig(directory.resolve("app.properties")); active.set("db.user", "database-one"); active.set("ai.apiKey", "private-ai-token");
        ConfigProfiles profiles = new ConfigProfiles(active); profiles.save("db", "数据库一", active);
        var profile = profiles.list("db").get(0); String text = Files.readString(profile.path()); assertFalse(text.contains("private-ai-token"));
        active.set("db.user", "database-two"); active.set("ai.apiKey", "new-api-token"); AppConfig selected = profiles.select(active, "db", profile);
        assertEquals("database-one", selected.get("db.user")); assertEquals("new-api-token", selected.get("ai.apiKey")); assertEquals("database-two", active.get("db.user"));
        active.applyAndSave(selected); assertEquals("database-one", new AppConfig(active.path()).get("db.user"));
    }
    @Test void defaultReleaseSettingsLoadOnlyWhenUserSettingsAbsent() throws Exception {
        Files.writeString(directory.resolve("course-demo.properties"), "db.user=demo-user\ncourse.demo.db=true\nai.protocol=openai-compatible\n");
        AppConfig active = new AppConfig(directory.resolve("application.properties")); assertTrue(active.usesCourseDemo()); assertEquals("demo-user", active.get("db.user"));
        AppConfig own = active.copy(); own.set("db.user", "own-user"); own.set("course.demo.db", "false"); active.applyAndSave(own);
        assertEquals("own-user", new AppConfig(active.path()).get("db.user")); assertEquals("demo-user", active.courseDefaults().get("db.user"));
    }
}
