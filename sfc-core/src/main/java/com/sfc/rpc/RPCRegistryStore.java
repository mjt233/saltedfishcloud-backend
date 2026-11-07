package com.sfc.rpc;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * RPC注册共享存储。
 * <br>
 * 该组件仅负责维护RPC处理器与客户端代理缓存，不参与具体的请求发送与接收逻辑，
 * 以便让{@link RPCRegistry}与{@link RPCInvoker}都依赖同一份共享状态，避免二者互相依赖。
 */
@Component
public class RPCRegistryStore {
    /**
     * RPC请求处理器映射，key为RPC函数全名。
     */
    private final Map<String, RPCHandler<?>> handlerMap = new ConcurrentHashMap<>();

    /**
     * RPC客户端实例映射，key为服务类型，value为该类型对应的客户端实现列表。
     */
    private final Map<Class<?>, List<Object>> rpcClientMap = new ConcurrentHashMap<>();

    /**
     * RPC客户端懒加载代理缓存，key为服务类型。
     */
    private final Map<Class<?>, Object> rpcClientLazyMap = new ConcurrentHashMap<>();

    /**
     * 注册RPC请求处理器。
     *
     * @param functionName RPC函数全名
     * @param handler      处理器实现
     * @param <T>          响应结果类型
     */
    public <T> void registerRpcHandler(String functionName, RPCHandler<T> handler) {
        handlerMap.put(functionName, handler);
    }

    /**
     * 获取指定函数名对应的RPC请求处理器。
     *
     * @param functionName RPC函数全名
     * @return 处理器，不存在时返回null
     */
    public RPCHandler<?> getRpcHandler(String functionName) {
        return handlerMap.get(functionName);
    }

    /**
     * 判断指定函数名是否已注册RPC请求处理器。
     *
     * @param functionName RPC函数全名
     * @return 已注册则返回true
     */
    public boolean containsRpcHandler(String functionName) {
        return handlerMap.containsKey(functionName);
    }

    /**
     * 为指定服务类型追加一个RPC客户端实例。
     *
     * @param clazz  服务类型
     * @param client RPC客户端实例
     */
    public void addRpcClient(Class<?> clazz, Object client) {
        rpcClientMap.computeIfAbsent(clazz, ignored -> new ArrayList<>()).add(client);
    }

    /**
     * 获取指定服务类型关联的所有RPC客户端实例。
     *
     * @param clazz 服务类型
     * @return 客户端实例列表，不存在时返回null
     */
    public List<Object> getRpcClients(Class<?> clazz) {
        return rpcClientMap.get(clazz);
    }

    /**
     * 获取或创建指定服务类型对应的懒加载代理。
     *
     * @param clazz    服务类型
     * @param supplier 懒加载代理创建器
     * @param <T>      服务类型
     * @return 懒加载代理实例
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCreateLazyRpcClient(Class<T> clazz, Supplier<T> supplier) {
        return (T) rpcClientLazyMap.computeIfAbsent(clazz, ignored -> supplier.get());
    }
}


