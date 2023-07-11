package com.sfc.rpc;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public interface RPCManager {

    /**
     * 发起RPC请求，可接收多个响应
     * @param request 请求参数
     * @param resultType 响应结果数据类型
     * @param exceptCount   期望的响应数量
     */
    <T> List<RPCResponse<T>> call(RPCRequest request, Class<T> resultType, long exceptCount) throws IOException;

    /**
     * 发起RPC请求，可接收多个响应
     * @param request 请求参数
     * @param timeout 等待超时
     * @param resultType 响应结果数据类型
     * @param exceptCount   期望的响应数量
     */
    <T> List<RPCResponse<T>> call(RPCRequest request, Class<T> resultType, Duration timeout, long exceptCount) throws IOException;

    /**
     * 发起RPC请求
     * @param request 请求参数
     * @param timeout 等待超时
     * @param resultType 响应结果数据类型
     */
    <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException;

    /**
     * 发起RPC请求，默认2分钟超时
     * @param request 请求参数
     * @param resultType 响应结果数据类型
     */
    <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType) throws IOException;


    /**
     * 发起RPC请求，默认2分钟超时，不处理响应结果数据
     * @param request 请求参数
     */
    <T> RPCResponse<T> call(RPCRequest request) throws IOException;

    /**
     * 注册RPC请求处理器
     *
     * @param functionName 函数名称
     * @param handler      操作器
     */
    <T> void registerRpcHandler(String functionName, RPCHandler<T> handler);
}
