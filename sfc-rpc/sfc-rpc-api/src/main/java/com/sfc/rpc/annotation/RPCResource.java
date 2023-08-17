package com.sfc.rpc.annotation;

import java.lang.annotation.*;

/**
 * 标记字段或方法参数为RPC代理服务，接收从{@link com.sfc.rpc.RPCManager}中创建的RPC代理接口，对方法的调用即可发起RPC调用。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.FIELD,
        ElementType.METHOD
})
public @interface RPCResource {
}
