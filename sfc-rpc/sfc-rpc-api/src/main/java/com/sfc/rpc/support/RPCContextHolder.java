package com.sfc.rpc.support;

import com.sfc.rpc.RPCRequest;

public class RPCContextHolder {
    private final static ThreadLocal<RPCRequest> requestThreadLocal = new ThreadLocal<>();
    private final static ThreadLocal<Boolean> isIgnoreThreadLocal = new ThreadLocal<>();

    public static RPCRequest getRequest() {
        return requestThreadLocal.get();
    }

    public static void setRequest(RPCRequest request) {
        requestThreadLocal.set(request);
    }

    public static void remove() {
        isIgnoreThreadLocal.remove();
        requestThreadLocal.remove();
    }

    /**
     * 标记忽略当前的RPC请求
     */
    public static void setIsIgnore(boolean isIgnore) {
        isIgnoreThreadLocal.set(isIgnore);
    }

    /**
     * 当前RPC请求是否已被忽略处理
     */
    public static boolean isIgnore() {
        return Boolean.TRUE.equals(isIgnoreThreadLocal.get());
    }

    /**
     * 当前调用是否由RPC发起
     */
    public static boolean isFromRpc() {
        return getRequest() != null;
    }
}
