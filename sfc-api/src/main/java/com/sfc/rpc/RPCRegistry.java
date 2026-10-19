package com.sfc.rpc;

/**
 * RPC注册中心，负责RPC服务、客户端以及请求处理器的注册与获取。
 */
public interface RPCRegistry {
    /**
     * 将一个对象注册为RPC服务提供者，待注册对象的类应使用{@link com.sfc.rpc.annotation.RPCService}定义作用域，
     * 方法使用{@link com.sfc.rpc.annotation.RPCAction}作为响应方法。
     *
     * @param obj 待注册对象
     */
    void registerRPCService(Object obj);

    /**
     * 获取定义了RPC行为的类的RPC客户端实现，对该接口的调用会直接发起对应的RPC调用。<br>
     * 定义了RPC行为的类是指使用了{@link com.sfc.rpc.annotation.RPCService RPCService} 和
     * {@link com.sfc.rpc.annotation.RPCAction RPCAction}的类。
     *
     * @param clazz RPC服务类
     * @param <T> RPC服务类类型
     * @return RPC服务类的代理类
     * @see #registerRPCClient(Class)
     */
    <T> T getRPCClient(Class<T> clazz);

    /**
     * 将一个定义了RPC行为的类注册为RPC客户端。<br>
     * 定义了RPC行为的类是指使用了{@link com.sfc.rpc.annotation.RPCService RPCService} 和
     * {@link com.sfc.rpc.annotation.RPCAction RPCAction}的类。
     *
     * @param clazz 待注册类
     * @see #getRPCClient(Class)
     */
    void registerRPCClient(Class<?> clazz);

    /**
     * 注册RPC请求处理器。
     *
     * @param functionName 函数名称
     * @param handler 操作器
     * @param <T> 响应结果类型
     */
    <T> void registerRpcHandler(String functionName, RPCHandler<T> handler);
}

