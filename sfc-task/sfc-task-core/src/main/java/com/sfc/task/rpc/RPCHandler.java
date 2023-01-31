package com.sfc.task.rpc;

public interface RPCHandler<T> {
    RPCResponse<T> handleRpcRequest(RPCRequest request);
}
