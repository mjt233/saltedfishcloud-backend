package com.sfc.rpc.exception;

import com.sfc.rpc.RPCRequest;
import com.xiaotao.saltedfishcloud.exception.MessageException;
import lombok.Getter;

/**
 * 标识本次的RPC请求被所有的节点忽略，未能得到处理或响应。可能是请求的资源不存在或被所有节点拒绝处理。
 */
public class RPCIgnoreException extends RPCException implements MessageException {
    @Getter
    private final RPCRequest request;

    public RPCIgnoreException(RPCRequest request) {
        super(request.getFunctionName());
        this.request = request;
    }

    public RPCIgnoreException(RPCRequest request, String message) {
        super(message);
        this.request = request;
    }
}
