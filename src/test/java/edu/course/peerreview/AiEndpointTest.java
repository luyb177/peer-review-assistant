package edu.course.peerreview;

import edu.course.peerreview.ai.AiEndpoint;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.service.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AiEndpointTest {
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings = {"http://127.0.0.1:8317", "http://localhost:8317", "http://[::1]:8317", "https://api.anthropic.com", "https://example.com/gateway"})
    void permitsHttpsAndExactLoopbackHttp(String url) { assertNotNull(AiEndpoint.parse(url)); }
    @ParameterizedTest @ValueSource(strings = {"http://example.com", "http://localhost.example.com", "http://127.0.0.1.example.com", "http://127.0.0.1@evil.example", "ftp://localhost", "https://user:secret@example.com", "https://example.com?key=secret", "https://example.com#fragment", "not a url"})
    void rejectsInsecureOrCredentialBearingEndpoints(String url) { assertThrows(BusinessException.class, () -> AiEndpoint.parse(url)); }
    @Test void fallbackAutoTargetsOnlyOfficialEndpointAndAllowsExplicitOverride() {
        AppConfig config = new AppConfig(temporary.resolve("absent.properties"));
        assertTrue(config.refusalFallbackEnabled());
        config.set("ai.baseUrl", "http://127.0.0.1:8317"); assertFalse(config.refusalFallbackEnabled());
        config.set("ai.refusalFallback", "true"); assertTrue(config.refusalFallbackEnabled());
        config.set("ai.refusalFallback", "false"); assertFalse(config.refusalFallbackEnabled());
        config.set("ai.refusalFallback", "invalid"); assertThrows(BusinessException.class, config::refusalFallbackEnabled);
    }
}
