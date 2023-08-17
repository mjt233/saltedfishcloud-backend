package com.sfc.rpc.support;

import com.sfc.rpc.RPCHandler;
import com.sfc.rpc.RPCRequest;
import com.sfc.rpc.RPCResponse;
import com.sfc.rpc.annotation.RPCAction;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 使用类的某个方法作为RPC处理方法。
 * @param <T>   方法返回值
 */
@Slf4j
@Getter
@SuppressWarnings("unchecked")
public class RPCMethodActionHandler<T> implements RPCHandler<T> {
    private final Object originInstance;
    private final RPCActionDefinition rpcActionDefinition;
    private final Method rpcMethod;
    private final boolean autoIgnore;

    /**
     * @param originInstance        类实例
     * @param rpcActionDefinition   RPC方法定义信息，见{@link com.sfc.rpc.util.RPCActionDefinitionUtils#getRPCActionDefinition(Class)}
     */
    public RPCMethodActionHandler(Object originInstance, RPCActionDefinition rpcActionDefinition) {
        this.originInstance = originInstance;
        this.rpcActionDefinition = rpcActionDefinition;

        RPCAction rpcAction = rpcActionDefinition.getRpcAction();
        Method rpcMethod = rpcActionDefinition.getMethod();
        this.rpcMethod = rpcActionDefinition.getMethod();
        this.autoIgnore = rpcAction.nullAsIgnore() && rpcMethod.getReturnType() != Void.TYPE;

    }

    @Override
    public RPCResponse<T> handleRpcRequest(RPCRequest request) {
        RPCContextHolder.setRequest(request);
        try {
            Object[] args = MapperHolder.withTypeMapper.readValue(request.getParam(), List.class).toArray();
            Object result = rpcMethod.invoke(originInstance, args);
            if (result instanceof RPCResponse) {
                return (RPCResponse<T>)result;
            } else if ((result == null && autoIgnore) || Boolean.TRUE.equals(RPCContextHolder.isIgnore())) {
                return RPCResponse.ignore();
            } else {
                return (RPCResponse<T>) RPCResponse.success(result);
            }
        } catch (Throwable e) {
            log.error("[RPC Annotation Action]执行出错: ", e);
            return RPCResponse.error(e.getMessage());
        } finally {
            RPCContextHolder.remove();
        }
    }
}
