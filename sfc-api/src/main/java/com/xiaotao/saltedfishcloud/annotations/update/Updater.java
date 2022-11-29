package com.xiaotao.saltedfishcloud.annotations.update;

import java.lang.annotation.*;

/**
 * 定义更新器，被该注解标记的类注入到Spring Bean容器中时，将会为其构造对应的更新器并注册到更新管理器中。<br>
 * 在类中使用该注解后，在其方法体中使用{@link UpdateAction} 和 {@link RollbackAction} 可快速定义更新与回滚方法
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Updater {
    /**
     * 更新器的作用域，默认为系统核心模块的全局作用域。若使用插件作用域，需要将其设置为与插件的name一致
     */
    String value() default "";
}
