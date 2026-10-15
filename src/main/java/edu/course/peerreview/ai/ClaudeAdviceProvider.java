package edu.course.peerreview.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.*;
import com.anthropic.models.beta.messages.MessageCreateParams;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.service.*;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Collectors;

/** 官方 SDK 负责 HTTP 与 JSON；关闭自动重试，避免一次点击发起多次付费请求。 */
public final class ClaudeAdviceProvider implements AdviceProvider {
    private final AppConfig config;
    public ClaudeAdviceProvider(AppConfig config) { this.config = config; }
    @Override public String generate(PeerReviewService.AdviceInput input) {
        if (!config.aiEnabled()) throw new BusinessException("AI 尚未启用，请在本地配置中设置 ai.enabled=true。");
        if (config.aiKey().isBlank()) throw new BusinessException("未配置 AI API Key；请填写本地配置或 PEER_REVIEW_AI_KEY 环境变量。");
        URI uri = AiEndpoint.parse(config.get("ai.baseUrl"));
        var client = AnthropicOkHttpClient.builder().baseUrl(uri.toString()).apiKey(config.aiKey())
                .timeout(Duration.ofSeconds(config.timeoutSeconds())).maxRetries(0).build();
        try {
            var builder = MessageCreateParams.builder().model(config.get("ai.model")).maxTokens(16000)
                    .system(AdvicePrompt.SYSTEM).addUserMessage(AdvicePrompt.user(input));
            if (config.refusalFallbackEnabled()) builder.addBeta("server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
            var response = client.beta().messages().create(builder.build());
            String reason = response.stopReason().map(Object::toString).orElse("");
            if (reason.equals("refusal")) throw new BusinessException("模型未提供参考意见；请继续独立完成人工互评。");
            if (reason.equals("max_tokens")) throw new BusinessException("AI 输出达到长度限制，未保存不完整结果；可继续人工互评。");
            String text = response.content().stream().flatMap(b -> b.text().stream()).map(b -> b.text()).collect(Collectors.joining("\n"));
            return Validation.text(text, "AI 返回内容", 60000);
        } catch (UnauthorizedException e) { throw new BusinessException("AI 身份验证失败，请检查 API Key。"); }
        catch (PermissionDeniedException e) { throw new BusinessException("AI 账号无权访问该模型或服务。"); }
        catch (NotFoundException e) { throw new BusinessException("AI 模型或接口不存在，请检查模型名称和地址。"); }
        catch (RateLimitException e) { throw new BusinessException("AI 请求受限，请稍后手动重试；本次不自动重试。"); }
        catch (AnthropicServiceException e) { throw new BusinessException("AI 服务暂时无法返回有效结果，请检查配置或稍后手动重试。"); }
        catch (AnthropicIoException e) { throw new BusinessException("AI 网络连接失败或超时；不影响人工评分。"); }
    }
}
