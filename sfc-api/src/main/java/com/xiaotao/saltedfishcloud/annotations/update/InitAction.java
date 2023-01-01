package com.xiaotao.saltedfishcloud.annotations.update;

import java.lang.annotation.*;

/**
 * 定义一个被{@link Updater}注解标注的类的方法为初始化方法，该方法在系统/插件第一次被启动/加载时执行
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InitAction {
}
