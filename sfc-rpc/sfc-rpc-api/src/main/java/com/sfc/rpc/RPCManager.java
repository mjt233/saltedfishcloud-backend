package com.sfc.rpc;

import java.io.IOException;
import java.time.Duration;

public interface RPCManager {
    /**
     * 发起RPC请求
     */
    <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException;

    <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType) throws IOException;

    /**
     * 注册RPC请求处理器
     *
     * @param functionName 函数名称
     * @param handler      操作器
     */
    <T> void registerRpcHandler(String functionName, RPCHandler<T> handler);
}
