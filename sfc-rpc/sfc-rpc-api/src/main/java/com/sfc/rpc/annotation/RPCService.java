package com.sfc.rpc.annotation;

import java.lang.annotation.*;

/**
 * <strong>实验性功能</strong><br>
 * 定义该服务为RPC服务，在整个系统中RPC服务名称不能重复
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RPCService {
    /**
     * RPC服务命名空间，若留空则使用类名。
     */
    String namespace() default "";

    /**
     * 注册为服务提供者
     */
    boolean registerAsProvider() default true;

    /**
     * 注册为客户端
     */
    boolean registerAsClient() default true;
}
