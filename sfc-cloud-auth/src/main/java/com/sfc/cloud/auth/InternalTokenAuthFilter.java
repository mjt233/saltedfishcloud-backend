package com.sfc.cloud.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 委托令牌的 fail-closed 认证过滤器。
 * <p>
 * <b>除配置的放行路径外，无令牌一律 401</b>。这是一条硬约束，不是建议：
 * "微服务可内网不可达"是<b>目标</b>而不是<b>约束</b>——网络边界一旦被打破
 * （同网段扫描、误配 Nginx、容器网络打通），fail-open 的微服务就等于"任意读"。
 * </p>
 *
 * <h3>校验顺序</h3>
 * <ol>
 *     <li>{@code sfc.cloud.auth.enabled=false} ⇒ 不拦截（显式的、有意为之的降级）；</li>
 *     <li>路径规范化后命中放行规则 ⇒ 放行；</li>
 *     <li>取 {@code Authorization: Bearer <令牌>}，缺失 ⇒ <b>401</b>；</li>
 *     <li>{@link InternalTokenVerifier#verify} 校验，失败 ⇒ <b>401</b>（对外统一为"凭证无效"，不区分具体原因）；
 *     未知 {@code kid} 会在校验器内部触发一次 JWKS 回源。</li>
 * </ol>
 * <p>
 * 校验通过后把令牌载荷挂到请求上，业务代码通过
 * {@link InternalTokenContext#requireCurrentUid()} 取 uid——<b>uid 只能来自令牌的 {@code sub}</b>。
 * </p>
 * <p>
 * 本类<b>不做</b>作用域校验：受保护操作需要哪个作用域由端点的
 * {@link RequireInternalToken} 声明，由 {@link RequireInternalTokenInterceptor} 返回 403。
 * </p>
 */
@Slf4j
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    /** {@code Authorization} 头的 Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final ServiceAuthClientProperties properties;
    private final InternalTokenVerifier verifier;
    private final PublicPathMatcher publicPathMatcher;

    /**
     * 构造过滤器。
     *
     * @param properties 微服务侧验签配置
     * @param verifier   委托令牌验签器
     */
    public InternalTokenAuthFilter(ServiceAuthClientProperties properties, InternalTokenVerifier verifier) {
        this.properties = properties;
        this.verifier = verifier;
        this.publicPathMatcher = new PublicPathMatcher(properties.getPublicPaths());
    }

    /**
     * 执行 fail-closed 的令牌校验。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (publicPathMatcher.isPublic(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (token == null) {
            ErrorResponses.write(response, 401, "缺少内部服务凭证");
            return;
        }

        InternalToken verified;
        try {
            verified = verifier.verify(token);
        } catch (InternalTokenException e) {
            // 具体原因只进服务端日志，响应统一为"凭证无效"
            log.debug("内部服务令牌校验失败 uri={}: {}", request.getRequestURI(), e.getMessage());
            ErrorResponses.write(response, 401, "内部服务凭证无效");
            return;
        }

        InternalTokenContext.set(request, verified);
        if (log.isDebugEnabled()) {
            log.debug("内部服务令牌校验通过 uri={} uid={} aud={} scopes={}",
                    request.getRequestURI(), verified.uid(), verified.audience(), verified.scopes());
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从 {@code Authorization} 头提取 Bearer 令牌。
     * <p>
     * 刻意<b>只认</b> {@code Authorization: Bearer} 这一种载体，不引入任何自定义令牌请求头——
     * 与 core 侧既有的 Bearer 约定一致，也让中继（Nginx / 代理）无需额外配置转发规则。
     * </p>
     *
     * @param request HTTP 请求
     * @return 令牌字符串；缺失或为空时返回 {@code null}
     */
    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }
}
