package com.sfc.gateway.support;

/**
 * 网关与微服务之间的身份头契约，以及用户会话凭证的三个入口名（框架期）。
 *
 * <h3>为什么这些常量在网关工程内、而不是共享模块里</h3>
 * <p>
 * 网关（本工程）与微服务（{@code microservices/sfc-demo-service} 等）都遵循
 * <b>零咸鱼云依赖</b>的硬标准，谁都无法引用 {@code sfc-cloud-api} 里的常量。
 * 因此头名在这里"声明"，在微服务侧"再声明一次"，两侧靠<b>文档</b>而非 jar 保持一致：
 * 契约见 {@code docs/plan/microservice/microservice-gateway-design.md} 决策 3.3。
 * </p>
 *
 * <h3>唯一注入点</h3>
 * <p>
 * {@link #UID} 的值<b>只能</b>来自网关自己的本地验签结果（{@code GatewayTokenVerifier}），
 * 网关<b>不得</b>从任何请求参数 / 头 / 路径里取 uid；且写入之前，入站的一切 {@link #PREFIX}
 * 前缀头必须已被<b>剥离</b>——这两条合起来才是信任模式的完整防线。
 * </p>
 *
 * <h3>凭证三个入口：只读取，<b>不剥离</b></h3>
 * <p>
 * {@link #TOKEN_HEADER} / {@link #TOKEN_PARAM} / {@link #TOKEN_COOKIE} 现在是网关<b>验签时的取值来源</b>，
 * 同时其原值会<b>原样透传</b>给目标微服务：网关只做"验签 ＋ 过期"，
 * 下游需要凭证原值才能完成含会话白名单的完整校验。
 * </p>
 * <p>
 * ⚠️ 早期实现曾把这三个入口全部剥离（当时的判据是"微服务不该拿到通往 core 的钥匙"）。该行为已按
 * 校验责任划分的调整而<b>反转</b>，理由是：剥离之后下游就只能信任明文身份头，
 * 而"让下游能自行完整校验"比"少暴露一个凭证"对安全更有利。代价是长效凭证会出现在微服务及其
 * access log 中，因此"微服务侧禁止打印凭证"是一条部署侧硬约束（见 {@code docs/plan/todo.md}）。
 * </p>
 */
public final class SfcTrustHeaders {

    /**
     * 身份头的名字：网关写入、微服务读取。
     */
    public static final String UID = "X-Sfc-Uid";

    /**
     * 身份头前缀（小写比较）：入站的一切该前缀头都要被剥离。
     * <p>
     * 剥离是<b>无条件</b>的（对所有路由生效）：core 现在不读这个头，但将来 core 的内部接口
     * （阶段 6 的 {@code /api/internal/**}）会信它——若网关只对代理分支剥离，
     * 浏览器就能通过 {@code /api/**} 透传链路伪造身份直达 core 内部接口。
     * </p>
     */
    public static final String PREFIX = "x-sfc-";

    /**
     * 会话凭证的请求头名（与 core 的 {@code JwtUtils.AUTHORIZATION}、{@code SecureUtils.getToken} 一致）。
     * <p>网关验签取值的<b>第一优先级</b>入口；原值原样透传给目标微服务。</p>
     */
    public static final String TOKEN_HEADER = "Token";

    /**
     * 会话凭证的查询参数名（同样是 core 的取值入口之一）。
     * <p>
     * 它是 {@code /api/ws} 这类无法自定义请求头的握手场景的凭证入口：<b>透传时不得剥离</b>，
     * 否则下游基于 {@code ?Token=} 的握手会凭空变成"未登录"。
     * </p>
     */
    public static final String TOKEN_PARAM = "Token";

    /**
     * 会话凭证的 Cookie 名（与 core 的 {@code SecureUtils.getToken} 一致）。
     * <p>只读取这一个 Cookie 作为凭证；透传时不改写、不移除，其余 Cookie 也原样保留。</p>
     */
    public static final String TOKEN_COOKIE = "token";

    private SfcTrustHeaders() {
        // 常量容器，禁止实例化
    }
}
