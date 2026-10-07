package com.sfc.cloud.auth;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 微服务侧的委托令牌验签配置。
 * <p>
 * 绑定配置前缀 {@code sfc.cloud.auth}。
 * </p>
 *
 * <h3>fail-closed 是默认姿态</h3>
 * <p>
 * {@link #enabled} 默认为 {@code true}：只要本库在类路径上，就默认保护全部端点。
 * "内网不可达"是<b>目标</b>而不是<b>约束</b>——网络边界一旦被打破（同网段扫描、误配 Nginx、
 * 容器网络打通），fail-open 的微服务就等于"任意读"。
 * 确实需要临时关闭时，必须显式配置 {@code sfc.cloud.auth.enabled=false}。
 * </p>
 * <p>
 * 同理，{@code enabled=true} 但缺少 {@code jwks-url} 或 {@code service-name} 时
 * <b>启动直接失败</b>，而不是退化成一个"能启动但一律 401"（或更糟：一律放行）的状态。
 * </p>
 *
 * @see InternalTokenAuthFilter
 */
@Slf4j
@Getter
@Setter
@ConfigurationProperties(prefix = "sfc.cloud.auth")
public class ServiceAuthClientProperties implements InitializingBean {

    /**
     * 是否启用委托令牌校验，默认 {@code true}（fail-closed）。
     * <p>
     * 设为 {@code false} 会让本库的过滤器不再拦截任何请求——包括带
     * {@code @RequireInternalToken} 注解的端点。这是一个显式的、有意为之的降级开关，
     * 只应用于本地联调，<b>不应出现在生产配置里</b>。
     * </p>
     */
    private boolean enabled = true;

    /**
     * core 侧 JWKS 端点地址，例如 {@code http://127.0.0.1:8087/api/service-auth/jwks}。
     * <p>
     * 该端点<b>匿名可达</b>，可安全地配置为普通 HTTP 地址；公钥只能验签、不能签发，
     * 因此分发它没有安全后果。
     * </p>
     */
    private String jwksUrl;

    /**
     * 本微服务的服务名，例如 {@code sfc-demo-service}。
     * <p>
     * ⚠️ 它是 <b>{@code aud} 校验的基准</b>：本库要求 {@code aud == 本值}。
     * 这与 core 侧"<code>aud ∈ 已注册服务名集合</code>"的规则<b>刻意不同</b>——
     * 受众绑定要防的正是"给 A 的令牌被 B 接受"，即微服务之间的<b>横向重放</b>。
     * </p>
     */
    private String serviceName;

    /**
     * 期望的令牌签发方，默认 {@code sfc-core}，必须与令牌的 {@code iss} 一致。
     */
    private String issuer = "sfc-core";

    /**
     * 免鉴权放行路径（Ant 风格的 {@code /**} 后缀或精确路径）。
     * <p>
     * ⚠️ 匹配前会对请求路径做<b>规范化</b>：反复 URL 解码、折叠 {@code .} 与 {@code ..}，
     * 无法规范化到根之内的路径一律<b>视为受保护</b>（fail-closed）。
     * 否则"放行 {@code /static/**}"会因为 {@code /static/../api/task/create} 这类构造
     * 变成"放行任意路径"。
     * </p>
     */
    private List<String> publicPaths = new ArrayList<>(
            List.of("/static/**", "/favicon.ico", "/index.html",
                    "/actuator/health", "/actuator/health/**", "/actuator/info",
                    "/error"));

    /**
     * JWKS 本地缓存时长（秒），默认 300 秒。
     * <p>
     * 正常路径下验签<b>没有任何网络调用</b>：公钥从本地缓存按 {@code kid} 命中。
     * 只有缓存过期、或遇到未知 {@code kid} 时才回源。
     * </p>
     */
    private long jwksCacheSeconds = 300;

    /**
     * 连接 JWKS 端点的超时（毫秒）。
     */
    private long connectTimeoutMillis = 2000;

    /**
     * 读取 JWKS 响应的超时（毫秒）。
     */
    private long readTimeoutMillis = 3000;

    /**
     * 校验配置的完整性。
     * <p>
     * {@code enabled=false} 时跳过校验；否则 {@code jwks-url} 与 {@code service-name}
     * 必须显式提供——它们分别决定"去哪儿取公钥"和"我接受哪些受众的令牌"，
     * 缺任何一个都无法安全地完成鉴权。
     * </p>
     */
    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            log.warn("sfc.cloud.auth.enabled=false：委托令牌校验已被显式关闭，本服务的端点将不再要求内部令牌。"
                    + "该状态仅应出现在本地联调中。");
            return;
        }
        if (!StringUtils.hasText(jwksUrl)) {
            throw new IllegalStateException("sfc.cloud.auth.jwks-url 未配置：无法获取验签公钥。"
                    + "请配置 core 侧的自建 JWKS 端点地址（如 http://<core-host>:<port>/api/service-auth/jwks）。");
        }
        if (!StringUtils.hasText(serviceName)) {
            throw new IllegalStateException("sfc.cloud.auth.service-name 未配置：无法校验令牌的 aud（受众绑定）。"
                    + "请显式配置本微服务的服务名，它必须与 core 侧签发令牌时使用的 aud 一致。");
        }
        if (!StringUtils.hasText(issuer)) {
            throw new IllegalStateException("sfc.cloud.auth.issuer 不能为空。");
        }
        if (jwksCacheSeconds <= 0) {
            throw new IllegalStateException("sfc.cloud.auth.jwks-cache-seconds 必须大于 0。");
        }
        log.info("委托令牌校验已启用：serviceName={} aud规则=aud==自己 issuer={} jwksUrl={} 公钥缓存={}s",
                serviceName, issuer, jwksUrl, jwksCacheSeconds);
    }
}
