package com.sfc.rpc.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
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
}
