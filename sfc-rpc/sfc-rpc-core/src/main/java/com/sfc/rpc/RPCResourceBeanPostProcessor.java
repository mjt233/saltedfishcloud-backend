package com.sfc.rpc;

import com.sfc.rpc.annotation.RPCResource;
import com.sfc.rpc.annotation.RPCService;
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
    private List<Tuple2<RPCService, Object>> waitRegisterServiceBean = new ArrayList<>();
    private List<Tuple2<Object, Field>> waitInjectResourceBean = new ArrayList<>();


    public void clearCache() {
        // 释放内存
        waitInjectResourceBean = new ArrayList<>();
        waitRegisterServiceBean = new ArrayList<>();
    }

    @Override
    public Object postProcessBeforeInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        RPCService rpcServiceAnnotation = RPCActionDefinitionUtils.getRPCServiceAnnotation(clazz);

        // 注册RPC服务提供者和客户端
        // 若RPC服务管理器未完成初始化，则暂存注册信息，等到RPC服务管理器完成初始化后统一注册
        if( rpcServiceAnnotation != null) {
            Tuple2<RPCService, Object> tuple = Tuples.of(rpcServiceAnnotation, bean);
            if (rpcManager != null) {
                registerBeanToRPCManager(tuple);
            } else {
                waitRegisterServiceBean.add(tuple);
            }
        }

        // 对已注册为RPC客户端的接口注入到要求该RPC客户端的Bean字段中，该字段通常用@RPCResource标注用于获取对应的RPC客户端
        // 类似于@Autowired功能
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

        // 判断是否为RPC管理器完成初始化，如果是则把暂存的服务/客户端注册、客户端实例字段注入统一消费掉
        if (rpcManager == null && bean instanceof RPCManager) {
            rpcManager = (RPCManager) bean;
            if (!waitRegisterServiceBean.isEmpty()) {
                waitRegisterServiceBean.forEach(this::registerBeanToRPCManager);
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

    private void registerBeanToRPCManager(Tuple2<RPCService, Object> tuple) {
        RPCService rpcService = tuple.getT1();
        Object bean = tuple.getT2();

        if (rpcService.registerAsProvider()) {
            rpcManager.registerRPCService(bean);
        }
        if (rpcService.registerAsClient()) {
            rpcManager.registerRPCClient(bean.getClass());
        }
    }
}
