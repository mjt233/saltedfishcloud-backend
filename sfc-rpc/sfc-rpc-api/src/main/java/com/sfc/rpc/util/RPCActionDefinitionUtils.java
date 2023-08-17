package com.sfc.rpc.util;

import com.sfc.rpc.RPCManager;
import com.sfc.rpc.RPCRequest;
import com.sfc.rpc.annotation.RPCAction;
import com.sfc.rpc.annotation.RPCService;
import com.sfc.rpc.enums.RPCResponseStrategy;
import com.sfc.rpc.exception.RPCIgnoreException;
import com.sfc.rpc.support.RPCActionDefinition;
import com.xiaotao.saltedfishcloud.utils.ClassUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RPC行为定义工具类
 */
@UtilityClass
@Slf4j
public class RPCActionDefinitionUtils {
    private final static Map<Class<?>, RPCService> CLASS_RPC_SERVICE_CACHE = new Hashtable<>();
    private final static Map<Method, String> METHOD_IDENTIFY_CACHE = new ConcurrentHashMap<>();
    /**
     * 记录各个class的RPC方法注解定义信息
     * class -> 方法标识 -> RPC方法定义
     */
    private final static Map<Class<?>, Map<String, RPCActionDefinition>> ACTION_DEFINITION_MAP_CACHE = new ConcurrentHashMap<>();

    /**
     * 从bean中获取RPCService注解，优先级：类本身 > 父类 > 实现接口
     * @param clazz 待解析类
     * @return      获取到的注解，若获取不到则返回null
     */
    public static RPCService getRPCServiceAnnotation(Class<?> clazz) {
        return CLASS_RPC_SERVICE_CACHE.computeIfAbsent(clazz, k -> ClassUtils.getAnnotation(clazz, RPCService.class));
    }

    /**
     * 获取方法的唯一标识，场景：方法接口被继承或实现时，获取实现类的该接口方法时，需要对应到接口最原始的方法标识来进行匹配。
     * @param method    方法
     * @return          方法标识字符串
     */
    public static String getMethodIdentify(Method method) {
        return METHOD_IDENTIFY_CACHE.computeIfAbsent(method, k -> method.getName() +
                ":" +
                Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining("_"))
        );
    }

    /**
     * 根据类创建RPC客户端
     * @param clazz         带有RPC信息定义的类
     * @param rpcManager    RPC管理器
     * @return              该类的RPC客户端实现
     */
    @SuppressWarnings("unchecked")
    public static <T> T createRPCClient(Class<T> clazz, RPCManager rpcManager) {
        Map<String, RPCActionDefinition> actionDefinitionMap = getRPCActionDefinition(clazz);
        // 注册客户端
        Enhancer enhancer = new Enhancer();
        enhancer.setClassLoader(clazz.getClassLoader());
        enhancer.setSuperclass(clazz);
        enhancer.setCallback((InvocationHandler) (proxy, method, args) -> {
            RPCActionDefinition definition = actionDefinitionMap.get(getMethodIdentify(method));
            if (definition == null) {
                throw new UnsupportedOperationException(method + "不是有效的rpc方法");
            }
            RPCAction rpcAction = definition.getRpcAction();

            RPCRequest request = RPCRequest.builder()
                    .functionName(definition.getFullFunctionName())
                    .isReportIgnore(rpcAction.reportIgnore())
                    .param(MapperHolder.withTypeMapper.writeValueAsString(Arrays.asList(args)))
                    .build();

            if (definition.getStrategy() == RPCResponseStrategy.ONLY_ACCEPT_ONE) {
                try {
                    return rpcManager.call(request, method.getReturnType()).getResult();
                } catch (RPCIgnoreException e) {
                    throw new RPCIgnoreException(request, rpcAction.ignoreMessage());
                }

            } else {
                return rpcManager.callAll(request, method.getReturnType())
                        .stream()
                        .map(e -> (List<?>)e.getResult())
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            }

        });

        return (T)enhancer.create();
    }

    /**
     * 获取类的所有RPC方法定义信息
     * @param clazz 待检测的类
     * @return  key - {@link #getMethodIdentify(Method)}
     */
    public static Map<String, RPCActionDefinition> getRPCActionDefinition(Class<?> clazz) {
        String namespace = getRPCServiceNamespace(clazz);
        return ACTION_DEFINITION_MAP_CACHE.computeIfAbsent(clazz, k -> Stream.concat(
                        Arrays.stream(clazz.getMethods()),
                        Arrays.stream(clazz.getInterfaces()).flatMap(e -> Arrays.stream(e.getMethods()))
                )
                .filter(e -> e.getAnnotation(RPCAction.class) != null)
                .collect(Collectors.toMap(
                        RPCActionDefinitionUtils::getMethodIdentify,
                        e -> {
                            RPCAction rpcAction = e.getAnnotation(RPCAction.class);
                            return RPCActionDefinition.builder()
                                    .rpcAction(rpcAction)
                                    .strategy(rpcAction.strategy())
                                    .method(e)
                                    .fullFunctionName(getRPCActionFunctionName(namespace, e))
                                    .build();
                        },
                        (oldVal, newVal) -> oldVal
                )));
    }

    /**
     * 获取rpc服务的命名空间
     * @param clazz rpc服务类
     * @return      命名空间
     */
    public static String getRPCServiceNamespace(Class<?> clazz) {
        RPCService rpcService = getRPCServiceAnnotation(clazz);
        if (rpcService == null) {
            throw new IllegalArgumentException(clazz + "不是有效的rpc服务");
        }

        if (StringUtils.hasText(rpcService.namespace())) {
            return rpcService.namespace();
        } else {
            return clazz.getName().replaceAll("\\.", "_");
        }
    }

    /**
     * 获取rpc动作方法全名
     * @param namespace 命名空间
     * @param method    rpc动作执行方法
     * @return          rpc方法全名
     */
    public static String getRPCActionFunctionName(String namespace, Method method) {
        RPCAction rpcAction = method.getAnnotation(RPCAction.class);
        if (rpcAction == null) {
            throw new IllegalArgumentException(method + "不是有效的rpc方法");
        }
        return "rpc_func::" + namespace + "::" +
                Optional.of(rpcAction.value())
                        .filter(StringUtils::hasText)
                        .orElseGet(() -> {
                            StringJoiner joiner = new StringJoiner("_");
                            joiner.add(method.getName() + "_");
                            for (Class<?> parameterType : method.getParameterTypes()) {
                                joiner.add(parameterType.getSimpleName());
                            }
                            return joiner.toString();
                        });
    }
}
