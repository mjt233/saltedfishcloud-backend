package com.sfc.rpc;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * RPC调用器，负责向集群节点发起RPC调用请求并接收响应。
 */
public interface RPCInvoker {

    /**
     * 对所有节点发起RPC请求，接收多个响应，即使请求报告了忽略处理也会接收。
     * @param request 请求参数
     * @param resultType 响应结果数据类型
     */
    <T> List<RPCResponse<T>> callAll(RPCRequest request, Class<T> resultType) throws IOException;

    /**
     * 对所有节点发起RPC请求，接收多个响应
     * @param request 请求参数
     * @param timeout 等待超时
     * @param resultType 响应结果数据类型
     */
    <T> List<RPCResponse<T>> callAll(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException;

    /**
     * 发起RPC请求。若所有集群节点报告了忽略处理则抛出{@link com.sfc.rpc.exception.RPCIgnoreException}
     * @param request 请求参数
     * @param timeout 等待超时
     * @param resultType 响应结果数据类型
     */
    <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType, Duration timeout) throws IOException;

    /**
     * 发起RPC请求，默认2分钟超时。若所有集群节点报告了忽略处理则抛出{@link com.sfc.rpc.exception.RPCIgnoreException}
     * @param request 请求参数
     * @param resultType 响应结果数据类型
     */
    <T> RPCResponse<T> call(RPCRequest request, Class<T> resultType) throws IOException;

    /**
     * 发起RPC请求，默认2分钟超时，不处理响应结果数据。若所有集群节点报告了忽略处理则抛出{@link com.sfc.rpc.exception.RPCIgnoreException}
     * @param request 请求参数
     */
    <T> RPCResponse<T> call(RPCRequest request) throws IOException;
}

