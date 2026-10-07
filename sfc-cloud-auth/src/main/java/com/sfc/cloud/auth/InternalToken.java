package com.sfc.cloud.auth;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 校验通过的委托令牌载荷。
 * <p>
 * 本库<b>只读 {@code sub} 与 {@code scope}</b>，不解读任何业务语义：
 * </p>
 * <ul>
 *     <li>{@link #uid()} 是业务代码<b>唯一</b>合法的用户身份来源。
 *     微服务的业务代码不得读取任何名为 {@code uid} 的请求参数——
 *     信任边界是结构性的，不是"约定忽略某个参数"；</li>
 *     <li>{@link #scopes()} 用于 {@code @RequireInternalToken} 声明的受保护操作。</li>
 * </ul>
 *
 * @param uid      用户 id，来自令牌的 {@code sub}
 * @param scopes   授权范围集合，来自令牌的 {@code scope}
 * @param audience 目标服务名，来自令牌的 {@code aud}
 * @param jwtId    令牌 id，来自 {@code jti}，仅用于日志关联
 */
public record InternalToken(long uid, Set<String> scopes, String audience, String jwtId) {

    /**
     * 紧凑构造器：对作用域集合做防御性拷贝并保证不可变。
     *
     * @param uid      用户 id
     * @param scopes   作用域集合，可为 {@code null}
     * @param audience 目标服务名
     * @param jwtId    令牌 id
     */
    public InternalToken {
        scopes = scopes == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
    }

    /**
     * 判断令牌是否具备指定作用域。
     *
     * @param scope 作用域名称，如 {@code internal.read}
     * @return 具备时返回 {@code true}
     */
    public boolean hasScope(String scope) {
        return scope != null && scopes.contains(scope);
    }
}
