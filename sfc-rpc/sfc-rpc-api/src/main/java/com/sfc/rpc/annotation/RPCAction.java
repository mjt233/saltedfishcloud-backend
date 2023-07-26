package com.sfc.rpc.annotation;

import com.sfc.rpc.enums.RPCResponseStrategy;

import java.lang.annotation.*;

/**
 * 定义该方法为RPC响应动作
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RPCAction {
    /**
     * 动作名称，同一个RPC服务命名空间下动作名称不能重复。若留空则使用方法名+参数类型的组合作为动作名。
     */
    String value() default "";

    /**
     * RPC响应策略
     */
    RPCResponseStrategy strategy() default RPCResponseStrategy.ONLY_ACCEPT_ONE;

    /**
     * 返回null时，是否默认设置为请求被忽略<br>
     * 仅在方法返回值不为void时生效
     */
    boolean nullAsIgnore() default true;

    /**
     * 忽略处理时是否响应“已忽略”<br>
     * 在一些需要马上得知请求在整个集群上是否得到被处理的功能时会有用，能通知到发起者系统所有节点都无法处理本次请求。
     */
    boolean reportIgnore() default true;
}
