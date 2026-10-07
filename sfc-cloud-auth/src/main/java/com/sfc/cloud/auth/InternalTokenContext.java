package com.sfc.cloud.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前请求的委托令牌上下文。
 * <p>
 * 本类是微服务业务代码获取用户身份<b>唯一</b>合法的入口：
 * </p>
 * <pre>{@code
 * Long uid = InternalTokenContext.requireCurrentUid();   // uid 只能来自令牌的 sub
 * }</pre>
 * <p>
 * <b>不得</b>从请求参数中读取任何名为 {@code uid} 的值。信任边界要求是<b>结构性</b>的
 * ——"uid 根本不是参数"，而不是"有一个参数但我们约定不看它"。
 * </p>
 */
public final class InternalTokenContext {

    /** 请求属性名，挂载校验通过的令牌载荷 */
    private static final String ATTRIBUTE_NAME = InternalTokenContext.class.getName() + ".TOKEN";

    private InternalTokenContext() {
    }

    /**
     * 把校验通过的令牌载荷挂到当前请求上。
     *
     * @param request HTTP 请求
     * @param token   令牌载荷
     */
    static void set(HttpServletRequest request, InternalToken token) {
        request.setAttribute(ATTRIBUTE_NAME, token);
    }

    /**
     * 从请求上取出令牌载荷。
     *
     * @param request HTTP 请求
     * @return 令牌载荷；未通过校验时返回 {@code null}
     */
    public static InternalToken get(HttpServletRequest request) {
        Object token = request.getAttribute(ATTRIBUTE_NAME);
        return token instanceof InternalToken internalToken ? internalToken : null;
    }

    /**
     * 取当前请求的令牌载荷。
     *
     * @return 令牌载荷；无请求上下文或未通过校验时返回 {@code null}
     */
    public static InternalToken current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return get(attributes.getRequest());
    }

    /**
     * 取当前用户 id（来自令牌的 {@code sub}）。
     *
     * @return 用户 id；未通过校验时返回 {@code null}
     */
    public static Long currentUid() {
        InternalToken token = current();
        return token == null ? null : token.uid();
    }

    /**
     * 取当前用户 id，缺失时直接抛错。
     * <p>
     * 受保护的业务代码应当用本方法而不是 {@link #currentUid()}：
     * 身份缺失属于<b>不该发生</b>的状态（fail-closed 的过滤器已经拦掉了无令牌请求），
     * 静默拿到 {@code null} 只会把一个鉴权缺陷变成一个更隐蔽的 NPE 或越权分支。
     * </p>
     *
     * @return 用户 id
     * @throws IllegalStateException 当前请求没有通过委托令牌校验时抛出
     */
    public static long requireCurrentUid() {
        Long uid = currentUid();
        if (uid == null) {
            throw new IllegalStateException("当前请求没有通过委托令牌校验，无法取得 uid："
                    + "uid 只能来自令牌的 sub，且必须由 fail-closed 的过滤器先行校验");
        }
        return uid;
    }
}
