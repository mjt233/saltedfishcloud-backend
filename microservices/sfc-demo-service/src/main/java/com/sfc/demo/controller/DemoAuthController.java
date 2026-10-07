package com.sfc.demo.controller;

import com.sfc.cloud.auth.InternalToken;
import com.sfc.cloud.auth.InternalTokenContext;
import com.sfc.cloud.auth.RequireInternalToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 委托令牌的验收探针端点（阶段 5）。
 * <p>
 * <b>本类刻意不提供任何业务能力</b>，它的存在意义是让"接口鉴权"这条链路可以被逐条实测：
 * 无令牌 401、aud 不符 401、已过期 401、作用域不足 403、篡改 uid 参数无效。
 * </p>
 *
 * <h3>⚠️ 为什么方法签名里没有 {@code uid} 参数</h3>
 * <p>
 * 这不是遗漏，而是本机制的核心约束：<b>信任边界是结构性的</b>。
 * 请求里带 {@code ?uid=999} 不会有任何作用——因为<b>根本没有一个 uid 参数可被篡改</b>，
 * 而不是"有一个参数但我们约定不看它"。
 * </p>
 * <p>
 * 本类的所有端点都通过 {@link InternalTokenContext#requireCurrentUid()} 取用户身份，
 * 而该值只能来自委托令牌的 {@code sub}。
 * </p>
 */
@RestController
@RequestMapping("/api/demo")
public class DemoAuthController {

    /**
     * 读取当前令牌的主体信息。
     * <p>
     * 需要 {@code internal.read} 作用域：令牌有效但缺少该作用域时由瘦库的拦截器返回 403。
     * </p>
     *
     * @return 令牌中的 uid、受众与作用域
     */
    @GetMapping("whoami")
    @RequireInternalToken("internal.read")
    public Map<String, Object> whoami() {
        InternalToken token = InternalTokenContext.current();
        Map<String, Object> result = new LinkedHashMap<>();
        // uid 只能来自令牌的 sub
        result.put("uid", InternalTokenContext.requireCurrentUid());
        result.put("audience", token == null ? null : token.audience());
        result.put("scopes", token == null ? null : token.scopes());
        result.put("jwtId", token == null ? null : token.jwtId());
        return result;
    }

    /**
     * 写入类操作的作用域探针。
     * <p>
     * 需要 {@code internal.write}：持只读令牌（{@code internal.read}）访问时必须是 <b>403</b>。
     * 这条正是"数据面令牌不得复用控制面高权限作用域"的落地检验。
     * </p>
     *
     * @return 令牌中的 uid
     */
    @PostMapping("write-probe")
    @RequireInternalToken("internal.write")
    public Map<String, Object> writeProbe() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uid", InternalTokenContext.requireCurrentUid());
        result.put("operation", "write-probe");
        return result;
    }
}
