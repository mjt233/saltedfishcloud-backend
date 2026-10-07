package com.xiaotao.saltedfishcloud.constant;

/**
 * 内部服务调用（委托令牌）的授权范围常量。
 * <p>
 * 本常量类服务于<b>跨进程的服务鉴权机制</b>——core 签发委托令牌、微服务离线验签，
 * 与开放平台对外的 OAuth2 授权范围是<b>两套彼此独立的体系</b>：
 * </p>
 * <ul>
 *     <li>取值<b>刻意不复用</b> {@link StandardScopes} 中的开放平台 scope 值（如 {@code storage_read}），
 *     避免"同一个字符串在两套体系里含义不同、却会一起变更"的隐患；</li>
 *     <li>授权模型复用既有的 {@link com.xiaotao.saltedfishcloud.annotations.RequireScope} 注解与
 *     {@code RequireScopeAspect} 切面（二者只检查 SecurityContext 中的 {@code SCOPE_*} authority，
 *     无任何 OAuth 运行时依赖），但作用域的<b>值完全独立</b>。</li>
 * </ul>
 * <p>
 * 委托令牌的 {@code scope} claim 由 core 签发时写入，微服务与 core 的 {@code /api/internal/**}
 * 验签后转换为 {@code SCOPE_<value>} authority，再由 {@code @RequireScope} 在受保护操作上强制校验。
 * </p>
 *
 * @see StandardScopes
 * @see com.xiaotao.saltedfishcloud.annotations.RequireScope
 */
public final class ServiceAuthScopes {

    private ServiceAuthScopes() {
    }

    /**
     * 内部只读范围：允许通过内部 API 读取网盘数据（如列举目录、读取文件元信息）。
     * <p>
     * 数据面（下载）令牌<b>只应</b>授予该范围，不得授予 {@link #WRITE}——
     * 数据面令牌以 URL query 为载体，存在经 {@code Referer}、浏览器历史与各类代理日志泄漏的固有风险。
     * </p>
     */
    public static final String READ = "internal.read";

    /**
     * 内部写入范围：允许通过内部 API 修改网盘数据（如上传、删除、移动）。
     */
    public static final String WRITE = "internal.write";
}
