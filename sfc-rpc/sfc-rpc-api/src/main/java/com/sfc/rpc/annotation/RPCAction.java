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
     * 是否将结果扁平化处理<br>
     * 这在将单个节点返回List类型时会非常有用。如：两个节点返回值为:<br>
     * <code>["a", "b"], ["c", "c"]</code> <br>
     *
     * 经过汇总后会处理为：<br>
     *
     * <code>[["a", "b"], ["c", "c"]]</code><br>
     *
     * 开启扁平化处理后，结果将会处理为：["a", "b", "c", "d"]
     */
    boolean isFlat() default false;

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

    /**
     * 作为客户端时，当请求被所有集群节点忽略后的异常提示语。
     */
    String ignoreMessage() default "请求的资源不存在或系统拒绝处理";
}
