package com.sfc.rpc;

import com.sfc.rpc.support.RPCMethodActionHandler;
import com.sfc.rpc.util.RPCActionDefinitionUtils;
import com.xiaotao.saltedfishcloud.utils.ClassUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.LazyLoader;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认RPC注册中心实现。
 * <p>
 * 该实现负责：
 * <ul>
 *     <li>将带有RPC定义的Bean注册为服务提供者</li>
 *     <li>维护RPC客户端代理的注册与懒加载获取</li>
 *     <li>维护RPC函数与处理器的映射关系</li>
 * </ul>
 * 调用行为由{@link RPCInvoker}负责，本类仅在创建客户端代理时依赖调用器。
 */
@Slf4j
@Component
public class DefaultRPCRegistry implements RPCRegistry {
    /**
     * RPC调用器。
     */
    private final RPCInvoker rpcInvoker;

    /**
     * RPC共享注册存储。
     */
    private final RPCRegistryStore rpcRegistryStore;

    /**
     * 实例化默认RPC注册中心。
     *
     * @param rpcInvoker        RPC调用器
     * @param rpcRegistryStore  RPC共享注册存储
     */
    @Autowired
    public DefaultRPCRegistry(RPCInvoker rpcInvoker, RPCRegistryStore rpcRegistryStore) {
        this.rpcInvoker = rpcInvoker;
        this.rpcRegistryStore = rpcRegistryStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerRPCService(Object obj) {
        Class<?> clazz = obj.getClass();
        RPCActionDefinitionUtils.getRPCActionDefinition(clazz).forEach((ignored, def) ->
                registerRpcHandler(def.getFullFunctionName(), new RPCMethodActionHandler<>(obj, def))
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerRPCClient(Class<?> clazz) {
        Object client = RPCActionDefinitionUtils.createRPCClient(clazz, rpcInvoker);
        bindRpcClient(clazz, client);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T getRPCClient(Class<T> clazz) {
        return rpcRegistryStore.getOrCreateLazyRpcClient(clazz, () -> createLazyRpcClient(clazz));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void registerRpcHandler(String functionName, RPCHandler<T> handler) {
        if (rpcRegistryStore.containsRpcHandler(functionName)) {
            log.warn("重复注册函数:{}", functionName);
        }
        rpcRegistryStore.registerRpcHandler(functionName, handler);
    }

    /**
     * 创建指定服务类型的懒加载RPC客户端。
     *
     * @param clazz 服务类型
     * @param <T>   服务类型
     * @return 懒加载RPC客户端
     */
    @SuppressWarnings("unchecked")
    protected <T> T createLazyRpcClient(Class<T> clazz) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        enhancer.setClassLoader(clazz.getClassLoader());
        enhancer.setCallback((LazyLoader) () -> resolveRpcClient(clazz));
        return (T) enhancer.create();
    }

    /**
     * 解析指定服务类型对应的RPC客户端实例。
     *
     * @param clazz 服务类型
     * @param <T>   服务类型
     * @return RPC客户端实例
     */
    protected <T> Object resolveRpcClient(Class<T> clazz) {
        List<Object> objects = rpcRegistryStore.getRpcClients(clazz);
        if (objects == null) {
            T client = RPCActionDefinitionUtils.createRPCClient(clazz, rpcInvoker);
            bindRpcClient(clazz, client);
            return client;
        }
        if (objects.size() > 1) {
            throw new IllegalArgumentException("与" + clazz + "关联的RPC服务存在多个");
        }
        return objects.getFirst();
    }

    /**
     * 将一个RPC客户端实例绑定到类继承树与接口继承树上，便于按父类或接口获取。
     *
     * @param clazz  服务类型
     * @param client RPC客户端实例
     */
    protected void bindRpcClient(Class<?> clazz, Object client) {
        ClassUtils.visitExtendsPath(clazz, parentClass -> {
            rpcRegistryStore.addRpcClient(parentClass, client);
            return true;
        });
        ClassUtils.visitImplementsPath(clazz, interfaceClass -> {
            rpcRegistryStore.addRpcClient(interfaceClass, client);
            return true;
        });
    }
}


