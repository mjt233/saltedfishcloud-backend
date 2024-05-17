package com.sfc.rpc.support;

import com.sfc.rpc.annotation.RPCAction;
import com.sfc.rpc.enums.RPCResponseStrategy;
import lombok.Builder;
import lombok.Getter;

import java.lang.reflect.Method;

@Getter
@Builder
public class RPCActionDefinition {

    /**
     * 完整RPC函数名称
     */
    private String fullFunctionName;

    /**
     * 响应策略
     */
    private RPCResponseStrategy strategy;

    /**
     * 是否扁平化处理list
     */
    private boolean isFlat;

    /**
     * 方法
     */
    private Method method;

    /**
     * 原始注解
     */
    private RPCAction rpcAction;
}
