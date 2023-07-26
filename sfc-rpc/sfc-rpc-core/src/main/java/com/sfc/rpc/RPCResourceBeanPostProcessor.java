package com.sfc.rpc;

import com.sfc.rpc.annotation.RPCResource;
import com.sfc.rpc.annotation.RPCService;
import com.sfc.rpc.util.RPCServiceProxyUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RPCResourceBeanPostProcessor implements BeanPostProcessor {
    private RPCManager rpcManager;
    private final List<Object> waitRegisterBean = new ArrayList<>();
    private final List<Tuple2<Object, Field>> waitInjectBean = new ArrayList<>();

    @Override
    public Object postProcessBeforeInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        if( RPCServiceProxyUtils.getRPCServiceAnnotation(clazz) != null) {
            if (rpcManager != null) {
                rpcManager.registerRPCService(bean);
            } else {
                waitRegisterBean.add(beanName);
            }
        }

        Arrays.stream(clazz.getDeclaredFields())
                .filter(e -> e.getAnnotation(RPCResource.class) != null)
                .forEach(field -> {
                    field.setAccessible(true);
                    if (rpcManager == null) {
                        waitInjectBean.add(Tuples.of(bean, field));
                    } else {
                        try {
                            field.set(bean, rpcManager.getRPCService(field.getType()));
                        } catch (IllegalAccessException e) {
                            throw new IllegalArgumentException("注入RPC服务出错", e);
                        }
                    }

                });

        if (rpcManager == null && bean instanceof RPCManager) {
            rpcManager = (RPCManager) bean;
            if (!waitRegisterBean.isEmpty()) {
                waitRegisterBean.forEach(rpcManager::registerRPCService);
            }
            if (!waitInjectBean.isEmpty()) {
                for (Tuple2<Object, Field> tuple : waitInjectBean) {
                    Object obj = tuple.getT1();
                    Field field = tuple.getT2();
                    try {
                        field.set(obj, rpcManager.getRPCService(field.getType()));
                    } catch (IllegalAccessException e) {
                        throw new IllegalArgumentException("注入RPC服务出错", e);
                    }
                }
            }
        }
        return bean;
    }
}
