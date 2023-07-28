package com.sfc.rpc;

import com.sfc.rpc.annotation.RPCResource;
import com.sfc.rpc.util.RPCActionDefinitionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * RPC资源bean的注解自动处理器，进行以下工作：
 * <ol>
 *     <li>扫描注册的Bean是否带有{@link com.sfc.rpc.annotation.RPCService}注解。如果有，则将其bean实例注册为RPC服务提供者和bean的类注册为RPC客户端</li>
 *     <li>扫描注册的Bean是否带有被{@link com.sfc.rpc.annotation.RPCResource}标注的字段。如果有，则向字段注入该字段类型的RPC客户端实现</li>
 * </ol>
 * <br>
 * 若在不通过Spring而是手动管理的对象中，同样可以使用:
 * <ol>
 *     <li>{@link RPCManager#registerRPCClient(Class)} - 注册RPC客户端</li>
 *     <li>{@link RPCManager#getRPCClient(Class)} - 获取RPC客户端实现</li>
 *     <li>{@link RPCManager#registerRPCService(Object)} - 注册RPC服务提供者</li>
 * </ol>
 *
 */
public class RPCResourceBeanPostProcessor implements BeanPostProcessor {
    private RPCManager rpcManager;
    private List<Object> waitRegisterServiceBean = new ArrayList<>();
    private List<Tuple2<Object, Field>> waitInjectResourceBean = new ArrayList<>();


    public void clearCache() {
        // 释放内存
        waitInjectResourceBean = new ArrayList<>();
        waitRegisterServiceBean = new ArrayList<>();
    }

    @Override
    public Object postProcessBeforeInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        if( RPCActionDefinitionUtils.getRPCServiceAnnotation(clazz) != null) {
            if (rpcManager != null) {
                rpcManager.registerRPCService(bean);
                rpcManager.registerRPCClient(clazz);
            } else {
                waitRegisterServiceBean.add(bean);
            }
        }

        Arrays.stream(clazz.getDeclaredFields())
                .filter(e -> e.getAnnotation(RPCResource.class) != null)
                .forEach(field -> {
                    field.setAccessible(true);
                    if (rpcManager == null) {
                        waitInjectResourceBean.add(Tuples.of(bean, field));
                    } else {
                        try {
                            field.set(bean, rpcManager.getRPCClient(field.getType()));
                        } catch (IllegalAccessException e) {
                            throw new IllegalArgumentException("注入RPC服务出错", e);
                        }
                    }

                });

        if (rpcManager == null && bean instanceof RPCManager) {
            rpcManager = (RPCManager) bean;
            if (!waitRegisterServiceBean.isEmpty()) {
                waitRegisterServiceBean.forEach(rpcManager::registerRPCService);
                waitRegisterServiceBean.forEach(e -> rpcManager.registerRPCClient(e.getClass()));
            }
            if (!waitInjectResourceBean.isEmpty()) {
                for (Tuple2<Object, Field> tuple : waitInjectResourceBean) {
                    Object obj = tuple.getT1();
                    Field field = tuple.getT2();
                    try {
                        field.set(obj, rpcManager.getRPCClient(field.getType()));
                    } catch (IllegalAccessException e) {
                        throw new IllegalArgumentException("注入RPC服务出错", e);
                    }
                }
            }
        }
        return bean;
    }
}
