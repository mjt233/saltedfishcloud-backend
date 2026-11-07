package com.sfc.rpc;

public class DefaultRPCManager implements RPCManager {
    private final RPCInvoker invoker;
    private final RPCRegistry registry;
    public DefaultRPCManager(RPCInvoker invoker, RPCRegistry registry) {
        this.invoker = invoker;
        this.registry = registry;
    }

    @Override
    public RPCInvoker getInvoker() {
        return invoker;
    }

    @Override
    public RPCRegistry getRegistry() {
        return registry;
    }
}
