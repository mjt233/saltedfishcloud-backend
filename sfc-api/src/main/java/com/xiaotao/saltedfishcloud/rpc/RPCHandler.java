package com.xiaotao.saltedfishcloud.rpc;

public interface RPCHandler<T> {
    RPCResponse<T> handleRpcRequest(RPCRequest request);
}
