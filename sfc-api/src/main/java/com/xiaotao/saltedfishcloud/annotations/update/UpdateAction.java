package com.xiaotao.saltedfishcloud.annotations.update;

import java.lang.annotation.*;

/**
 * 定义一个被{@link Updater}注解标注的类的方法为更新体方法。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UpdateAction {
    /**
     * 更新的版本，当对应作用域的上一个版本低于该值时执行更新操作
     */
    String value();
}
