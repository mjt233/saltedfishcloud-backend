package com.sfc.rpc;

public interface RPCHandler<T> {
    RPCResponse<T> handleRpcRequest(RPCRequest request);
}
