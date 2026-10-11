package com.sfc.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关自有配置（{@code sfc.gateway.*}）。
 *
 * <p>
 * ⚠️ 类名刻意<b>不</b>叫 {@code GatewayProperties}：Spring Cloud Gateway 自己也有一个同名配置类（bean 名
 * {@code gatewayProperties}），同名的组件会让容器在启动时直接抛
 * {@code BeanDefinitionOverrideException}（实测踩过）。
 *
 * <p>
 * 刻意不占用咸鱼云的 {@code sys.} 前缀：网关是独立进程，它不读 core 的配置，core 也不读它的。
 * 自本版本起，两侧<b>连 HTTP 调用都没有了</b>——网关独立完成用户凭证的验签与全部代理判定。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "sfc.gateway")
public class SfcGatewayProperties {

    /**
     * 承载 JWT 密钥的环境变量名（约定，不参与绑定）。
     * <p>
     * 取值必须与 core 的会话密钥一致：core 把密钥存在数据库配置项 {@code sys.safe.token} 里
     * （首次启动随机生成，且支持在线修改），网关<b>不</b>读取 core 的配置，只从环境变量取。
     * 因此两侧的一致性由<b>部署</b>保证，缺失或漂移时的现象是"登录正常、{@code /api/ext/**} 全量 403"。
     * </p>
     */
    public static final String JWT_SECRET_ENV = "SFC_JWT_SECRET";

    /**
     * core 在注册中心里的服务名（默认 {@code sfc-core}）。
     * <p>
     * 用途有两处：① 透传路由的目标（yml 里的 {@code lb://${sfc.gateway.core-service}}）；
     * ② <b>自我转发阻断</b>——目标为 core 的请求一律 404/7001（提权路径）。
     * </p>
     */
    private String coreService = "sfc-core";

    /**
     * 网关自身在注册中心里的服务名。
     * <p>
     * 只用于<b>自我转发阻断</b>：目标为网关自身的请求一律 404/7001，否则 {@code /api/ext/sfc-gateway/**}
     * 会形成回环。默认取自 {@code spring.application.name}（yml 中显式写成占位符）。
     * </p>
     */
    private String selfServiceName = "sfc-gateway";

    /**
     * 代理分支（{@code /api/ext/**}）的路由 id。
     * <p>
     * 用于判定"这个请求该不该走验签、注入与改道"：只有该路由的请求才进代理分支，
     * core 透传路由（{@code /api/**} 等）必须保持完全透明。
     * </p>
     */
    private String proxyRouteId = "micro-proxy";

    /**
     * 微服务未在实例 metadata 声明 {@code sfc.api.prefix} 时使用的缺省下游前缀。
     * <p>
     * 该值原先由 core 的 {@code sys.service.gateway.default-api-prefix} 提供，配置归属随判定一起下沉到网关。
     * </p>
     */
    private String defaultApiPrefix = "/api";

    /**
     * 用户会话凭证的验签密钥（HS256 家族，与 core 的 {@code JwtUtils} 共用同一份密钥）。
     * <p>
     * 由环境变量 {@value #JWT_SECRET_ENV} 注入（yml 中写作 {@code ${SFC_JWT_SECRET:}}），
     * <b>没有硬编码默认值</b>：缺失或长度不足 32 字节时网关启动失败（fail-fast），
     * 而不是"起来了但所有代理请求都 403"。
     * </p>
     */
    private String jwtSecret;
}
