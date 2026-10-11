package com.sfc.gateway.auth;

import com.sfc.gateway.support.SfcTrustHeaders;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 会话凭证（JWT）的提取：三个入口，<b>顺序与 core 完全一致</b>。
 *
 * <h3>为什么顺序必须一致</h3>
 * <p>
 * core 的 {@code SecureUtils.getToken} 依次看 {@code Token} 请求头 → {@code ?Token=} 查询参数 →
 * {@code token} Cookie。网关按同样顺序取，且只取<b>一个</b>值——否则会出现"两侧看到不同的凭证"：
 * 例如请求同时带了 A 头与 B 参数，网关按 B 判通过、core 按 A 判失败，症状是
 * "网关放行、下游 403"，排查成本极高。
 * </p>
 *
 * <h3>凭证只被读取，不被剥离</h3>
 * <p>
 * 与早期实现的重大差异：token 的<b>原值会原样透传</b>给目标微服务（见设计决策 5 第 3~5 条），
 * 因此这里只做"读"，不做任何改写。透传的目的是让下游能用同一份凭证完成<b>自己的</b>完整校验
 * （core 的完整校验还包含 Redis 会话白名单，而网关只做验签 ＋ 过期）。
 * </p>
 */
public final class TokenExtractor {

    private TokenExtractor() {
        // 纯工具类，禁止实例化
    }

    /**
     * 按 core 的优先级顺序取出会话凭证。
     *
     * @param request 入站请求
     * @return 凭证原值；三个入口都没有时返回 {@code null}
     */
    public static String extract(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(SfcTrustHeaders.TOKEN_HEADER);
        if (token == null) {
            token = request.getQueryParams().getFirst(SfcTrustHeaders.TOKEN_PARAM);
        }
        if (token == null) {
            List<HttpCookie> cookies = request.getCookies().get(SfcTrustHeaders.TOKEN_COOKIE);
            // core 遍历同名 Cookie 时是"后来的覆盖先前的"（不 break），这里取最后一个以保持同一结果
            if (cookies != null && !cookies.isEmpty()) {
                token = cookies.get(cookies.size() - 1).getValue();
            }
        }
        return StringUtils.hasText(token) ? token : null;
    }
}
