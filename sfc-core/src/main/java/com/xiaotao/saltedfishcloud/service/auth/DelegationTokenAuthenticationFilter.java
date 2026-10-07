package com.xiaotao.saltedfishcloud.service.auth;

import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /api/internal/**} 专用链的委托令牌认证过滤器。
 * <p>
 * 把 {@code Authorization: Bearer <委托令牌>} 校验通过后的用户主体装载进 Spring Security 上下文，
 * 使 {@code @UID}、{@code @ValidPath}、{@code @RolesAllowed}、{@code @RequireScope}、
 * {@code ProtectBlocker} <b>全部原样生效、零改动</b>——因为它们读的都是 SecurityContext，而不是请求参数。
 * </p>
 *
 * <h3>信任边界：uid 只来自令牌的 {@code sub}</h3>
 * <p>
 * {@code /api/internal/**} 的方法签名里<b>根本不出现 uid 参数</b>，
 * 业务代码通过 {@code SecureUtils.getCurrentUid()} 取当前主体。
 * 于是"篡改 uid 越权"这类攻击<b>没有可篡改的入口</b>，
 * 而不是"有一个入口但我们约定不看它"——这是结构性安全与约定式安全的区别。
 * </p>
 *
 * <h3>失败语义</h3>
 * <table border="1">
 *     <caption>与设计文档 §3.3 对齐</caption>
 *     <tr><th>情形</th><th>响应</th><th>由谁产生</th></tr>
 *     <tr><td>{@code sys.service.auth.enabled=false}</td><td>401</td>
 *         <td>本过滤器不装载主体，链的 {@code anyRequest().authenticated()} 触发 401 入口点</td></tr>
 *     <tr><td>无令牌</td><td>401</td><td>同上，fail-closed</td></tr>
 *     <tr><td>验签失败 / 过期 / {@code iss} 不符 / {@code aud} 不符 / 用户不存在</td><td>401</td>
 *         <td>同上，对外统一为"凭证无效"，不区分具体原因</td></tr>
 *     <tr><td>令牌存在但密钥不可用（配置错误）</td><td><b>500</b></td>
 *         <td>本过滤器直接短路——配置错误应当显式暴露，而不是静默退化成 401</td></tr>
 *     <tr><td>令牌有效但 {@code scope} 不足</td><td>403</td>
 *         <td>端点的 {@code @RequireScope} ＋ {@code RequireScopeAspect}，<b>不在本过滤器中</b></td></tr>
 * </table>
 *
 * <h3>刻意不授予 {@code ROLE_OAUTH_USER}</h3>
 * <p>
 * 只授予用户既有的 authorities（{@code ROLE_COMMON} / {@code ROLE_ADMIN}）与由令牌 {@code scope}
 * 转换来的 {@code SCOPE_*}。{@code ROLE_OAUTH_USER} 是开放平台的概念，与内部服务调用无关。
 * </p>
 * <p>
 * 角色<b>由 core 按 {@code sub} 实时装载</b>，因此用户被删除 / 禁用时令牌自然失效，
 * 免费继承了既有的用户有效性语义。
 * </p>
 */
@Slf4j
public class DelegationTokenAuthenticationFilter extends OncePerRequestFilter {

    /** {@code Authorization} 头的 Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** {@code SCOPE_} authority 前缀，与 {@code RequireScopeAspect} 的拼接口径一致 */
    private static final String SCOPE_PREFIX = "SCOPE_";

    private final ServiceAuthProperties properties;
    private final DelegationTokenVerifier verifier;
    private final UserService userService;

    /**
     * 构造委托令牌认证过滤器。
     *
     * @param properties  委托令牌机制配置，提供开关与密钥可用性判定
     * @param verifier    委托令牌校验器
     * @param userService 用户服务，用于按 {@code sub} 装载主体
     */
    public DelegationTokenAuthenticationFilter(ServiceAuthProperties properties,
                                               DelegationTokenVerifier verifier,
                                               UserService userService) {
        this.properties = properties;
        this.verifier = verifier;
        this.userService = userService;
    }

    /**
     * 解析并校验委托令牌，成功时把用户主体设入 SecurityContext。
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
        // enabled=false 的语义是"关闭"而不是"放开"：不装载任何主体，由链上的 401 入口点拒绝
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 令牌存在但密钥不可用：配置错误，显式 500（若在此处静默跳过，调用方只会看到一个难以定位的 401）
        if (!properties.isKeyAvailable()) {
            log.error("收到 /api/internal 请求但签名密钥不可用，无法校验委托令牌；请检查 sys.service.auth.signing-key 配置");
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(500);
            response.getWriter().print(JsonResultImpl.getInstance(
                    500, null, "内部服务鉴权未正确配置：签名密钥不可用，无法校验委托令牌"));
            return;
        }

        DelegationTokenVerifier.VerifiedToken verified;
        try {
            verified = verifier.verify(token);
        } catch (InvalidDelegationTokenException e) {
            // 具体原因只进服务端日志，响应统一为"凭证无效"
            log.debug("委托令牌校验失败: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        User user = userService.getUserById(verified.uid());
        if (user == null) {
            log.debug("委托令牌的 sub 指向的用户不存在: uid={}", verified.uid());
            filterChain.doFilter(request, response);
            return;
        }

        UserPrincipal principal = UserPrincipal.from(user);
        List<GrantedAuthority> authorities = new ArrayList<>(principal.getAuthorities());
        verified.scopes().forEach(scope -> authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope)));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, token, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        if (log.isDebugEnabled()) {
            log.debug("委托令牌认证通过：uid={} aud={} scopes={} jti={}",
                    verified.uid(), verified.audience(), verified.scopes(), verified.jwtId());
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从 {@code Authorization} 头提取 Bearer 令牌。
     * <p>
     * 刻意<b>只认</b> {@code Authorization: Bearer} 这一种载体：与既有
     * {@code OidcAccessTokenFilter} / {@code McpApiKeyFilter} 的约定一致，
     * 且该头已在 CORS 白名单内。不引入任何自定义令牌请求头。
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
