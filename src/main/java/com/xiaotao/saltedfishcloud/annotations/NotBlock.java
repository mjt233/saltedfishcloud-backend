package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.*;

/**
 * 被该注解标记的方法不会被方法所在类的 @BlockWhileSwitching 影响 <br>
 * 即相当于类级阻塞注解的例外方法<br>
 * @see ReadOnlyBlock
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotBlock {
}
