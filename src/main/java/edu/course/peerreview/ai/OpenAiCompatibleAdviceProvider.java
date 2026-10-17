package edu.course.peerreview.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.service.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** OpenAI-compatible HTTP protocol adapter; separate from the Anthropic SDK adapter. */
public final class OpenAiCompatibleAdviceProvider implements AdviceProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AppConfig config;
    public OpenAiCompatibleAdviceProvider(AppConfig config) { this.config = config; }
    @Override public String generate(PeerReviewService.AdviceInput input) {
        if (!config.aiEnabled()) throw new BusinessException("AI 尚未启用，请在连接配置中启用。");
        if (config.aiKey().isBlank()) throw new BusinessException("未配置 AI API Key。");
        try {
            String body = JSON.writeValueAsString(Map.of("model", config.get("ai.model"), "max_tokens", 4096,
                    "messages", List.of(Map.of("role", "system", "content", AdvicePrompt.SYSTEM), Map.of("role", "user", "content", AdvicePrompt.user(input)))));
            HttpRequest request = HttpRequest.newBuilder(endpoint(config.get("ai.baseUrl"), "chat/completions"))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds())).header("Authorization", "Bearer " + config.aiKey())
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(request, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode());
            JsonNode choice = JSON.readTree(response.body()).path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText())) throw new BusinessException("AI 输出达到长度限制，未保存不完整结果。");
            return Validation.text(choice.path("message").path("content").asText(), "AI 返回内容", 60000);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException("AI 请求已中断，人工评分不受影响。"); }
        catch (java.io.IOException e) { throw new BusinessException("AI 网络连接、超时或响应格式错误，人工评分不受影响。"); }
    }
    public static URI endpoint(String base, String path) {
        URI checked = AiEndpoint.parse(base); String value = checked.toString().replaceAll("/+$", "");
        if (!value.endsWith("/v1")) value += "/v1";
        return URI.create(value + "/" + path);
    }
    public static void requireSuccess(int status) {
        if (status >= 200 && status < 300) return;
        if (status == 401 || status == 403) throw new BusinessException("AI 身份验证或权限检查失败，请检查 Key。");
        if (status == 429) throw new BusinessException("AI 请求受限，请稍后手动重试。");
        throw new BusinessException("AI 接口请求失败（HTTP " + status + "），请检查地址、协议和服务状态。");
    }
}
