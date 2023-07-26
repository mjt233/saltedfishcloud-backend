package com.sfc.rpc.support;

import com.sfc.rpc.RPCRequest;

import java.util.Optional;

public class RPCContextHolder {
    private final static ThreadLocal<RPCContext> contextThreadLocal = new ThreadLocal<>();

    /**
     * 获取当前的请求
     * @return 若当前不是在处理RPC响应或未绑定RPC上下文，则返回null
     */
    public static RPCRequest getRequest() {
        return Optional.ofNullable(contextThreadLocal.get())
                .map(RPCContext::getRequest)
                .orElse(null);
    }

    /**
     * 设置当前请求。若未设置RPCContext则会自动先创建。
     */
    public static void setRequest(RPCRequest request) {
        getOrCreateContext().setRequest(request);
    }

    /**
     * 获取当前RPCContext
     * @return 若当前不是在处理RPC响应或未绑定RPC上下文，则返回null
     */
    public static RPCContext getContext() {
        return contextThreadLocal.get();
    }

    /**
     * 设置当前RPCContext
     */
    public static void setContext(RPCContext context) {
        contextThreadLocal.set(context);
    }

    /**
     * 移除RPC上下文记录，通常会自动调用。若是手动setContext了，则需要手动调用remove防止内存溢出。
     */
    public static void remove() {
        contextThreadLocal.remove();
    }

    /**
     * 标记忽略当前的RPC请求
     */
    public static void setIsIgnore(boolean isIgnore) {
        getOrCreateContext().setIsIgnore(isIgnore);
    }

    /**
     * 当前RPC请求是否已被忽略处理
     * @return 若当前不是在处理RPC响应或未绑定RPC上下文，则返回null
     */
    public static Boolean isIgnore() {
        return Optional.ofNullable(contextThreadLocal.get())
                .map(RPCContext::getIsIgnore)
                .orElse(null);
    }

    /**
     * 当前调用是否由RPC发起
     */
    public static boolean isFromRpc() {
        return getContext() != null;
    }

    /**
     * 获取当前的上下文，若不存在则创建。
     */
    private static RPCContext getOrCreateContext() {
        RPCContext curContext = contextThreadLocal.get();
        if (curContext == null) {
            curContext = new RPCContext();
            contextThreadLocal.set(curContext);
        }
        return curContext;
    }
}
