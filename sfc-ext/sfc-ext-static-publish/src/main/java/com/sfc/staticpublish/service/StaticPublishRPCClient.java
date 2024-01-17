package com.sfc.staticpublish.service;

import com.sfc.rpc.annotation.RPCAction;
import com.sfc.rpc.annotation.RPCService;
import com.sfc.staticpublish.model.ServiceStatus;

import java.util.List;

@RPCService(namespace = "static_publish", registerAsProvider = false)
public interface StaticPublishRPCClient {

    /**
     * 获取所有节点的服务运行状态
     */
    @RPCAction
    List<ServiceStatus> getStatus();
}
