package edu.course.peerreview.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.service.*;
import java.net.http.*;
import java.time.Duration;

public final class AiServices {
    private AiServices() { }
    public static AdviceProvider provider(AppConfig active) {
        return input -> {
            AppConfig snapshot = active.copy();
            return switch (snapshot.get("ai.protocol")) {
                case "anthropic" -> new ClaudeAdviceProvider(snapshot).generate(input);
                case "openai-compatible" -> new OpenAiCompatibleAdviceProvider(snapshot).generate(input);
                default -> throw new BusinessException("未知 AI 协议，请选择 Anthropic 或 OpenAI 兼容接口。");
            };
        };
    }
    /** Authenticated models GET only: no completion, no student data and no auto retry. */
    public static String test(AppConfig candidate) {
        AppConfig c = candidate.copy(); AiEndpoint.parse(c.get("ai.baseUrl")); c.timeoutSeconds();
        if (c.aiKey().isBlank()) throw new BusinessException("请填写 API Key，或显式选择使用环境变量 Key。");
        Validation.text(c.get("ai.model"), "模型名称", 160);
        try {
            if (c.get("ai.protocol").equals("anthropic")) {
                var client = AnthropicOkHttpClient.builder().baseUrl(anthropicBase(c.get("ai.baseUrl"))).apiKey(c.aiKey())
                        .timeout(Duration.ofSeconds(c.timeoutSeconds())).maxRetries(0).build();
                var models = client.models().list().data();
                return "AI 连接与认证成功，模型列表可访问（" + models.size() + " 项）；未发起生成请求。";
            }
            if (!c.get("ai.protocol").equals("openai-compatible")) throw new BusinessException("请选择支持的 AI 协议。");
            HttpRequest request = HttpRequest.newBuilder(OpenAiCompatibleAdviceProvider.endpoint(c.get("ai.baseUrl"), "models"))
                    .timeout(Duration.ofSeconds(c.timeoutSeconds())).header("Authorization", "Bearer " + c.aiKey()).GET().build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(request, HttpResponse.BodyHandlers.ofString());
            OpenAiCompatibleAdviceProvider.requireSuccess(response.statusCode());
            var data = new ObjectMapper().readTree(response.body()).path("data");
            if (!data.isArray()) throw new BusinessException("接口返回的不是有效模型列表，请检查协议与地址。");
            return "AI 连接与认证成功，模型列表可访问（" + data.size() + " 项）；未发起生成请求。";
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException("AI 连通性测试已中断。"); }
        catch (java.io.IOException e) { throw new BusinessException("AI 连通性测试失败：网络、超时或响应格式异常。"); }
        catch (com.anthropic.errors.AnthropicException e) { throw new BusinessException("AI 连通性测试失败，请检查接口地址、Key、协议及网络。"); }
    }
    public static String anthropicBase(String base) { return AiEndpoint.parse(base).toString().replaceAll("/+$", "").replaceAll("/v1$", ""); }
}
