package com.sfc.cloud.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个端点需要通过委托令牌校验，并可指定所需的作用域。
 * <p>
 * 使用示例：
 * </p>
 * <pre>{@code
 * @RestController
 * public class DemoController {
 *
 *     @GetMapping("/api/demo/whoami")
 *     @RequireInternalToken("internal.read")
 *     public Object whoami() {
 *         // uid 只能来自令牌，绝不从请求参数读取
 *         return Map.of("uid", InternalTokenContext.requireCurrentUid());
 *     }
 * }
 * }</pre>
 * <p>
 * ⚠️ <b>本注解不是"是否鉴权"的开关</b>：{@link InternalTokenAuthFilter} 是
 * <b>fail-closed</b> 的，除配置的放行路径外，<b>所有</b>请求都要求有效令牌。
 * 本注解的语义是"这个方法还额外要求某个作用域"——
 * 缺少所需作用域时由 {@link RequireInternalTokenInterceptor} 返回 403。
 * </p>
 *
 * @see InternalTokenAuthFilter
 * @see RequireInternalTokenInterceptor
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireInternalToken {

    /**
     * 所需的作用域列表，取值见 core 侧 {@code ServiceAuthScopes}（{@code internal.read} / {@code internal.write}）。
     * <p>
     * 留空表示只要求"令牌有效"——此时 fail-closed 的过滤器已经完成了全部校验。
     * </p>
     *
     * @return 所需作用域；为空数组时不做额外作用域校验
     */
    String[] value() default {};
}
