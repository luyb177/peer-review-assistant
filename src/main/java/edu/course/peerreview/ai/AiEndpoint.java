package edu.course.peerreview.ai;

import edu.course.peerreview.service.BusinessException;
import java.net.URI;
import java.util.Locale;

/** 本机网关允许 HTTP；外部服务必须 HTTPS。不通过 DNS 查询扩大回环地址范围。 */
public final class AiEndpoint {
    private AiEndpoint() { }
    public static URI parse(String address) {
        URI uri;
        try { uri = URI.create(address); }
        catch (IllegalArgumentException | NullPointerException e) { throw new BusinessException("AI 地址不是有效 URI。"); }
        String host = uri.getHost();
        boolean local = host != null && switch (host.toLowerCase(Locale.ROOT)) {
            case "localhost", "127.0.0.1", "::1", "[::1]" -> true;
            default -> false;
        };
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        if (host == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (!secure && !("http".equalsIgnoreCase(uri.getScheme()) && local)))
            throw new BusinessException("AI 地址须使用 HTTPS；仅 localhost、127.0.0.1 和 ::1 允许 HTTP，且不能包含凭据、查询参数或片段。");
        return uri;
    }
}
