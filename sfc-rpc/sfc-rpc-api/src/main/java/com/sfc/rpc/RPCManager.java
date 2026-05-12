package com.sfc.rpc;

public interface RPCManager {
    RPCInvoker getInvoker();

    RPCRegistry getRegistry();
}
