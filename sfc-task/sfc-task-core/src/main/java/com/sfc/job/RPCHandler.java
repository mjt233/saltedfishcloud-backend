package com.sfc.job;

public interface RPCHandler<T> {
    RPCResponse<T> handleRpcRequest(RPCRequest request);
}
