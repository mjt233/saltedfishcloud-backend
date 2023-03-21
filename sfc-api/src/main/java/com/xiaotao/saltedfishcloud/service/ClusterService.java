package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;

import java.util.List;

/**
 * 集群服务
 */
public interface ClusterService {

    /**
     * 获取集群所有节点信息
     */
    List<ClusterNodeInfo> listNodes();

    /**
     * 获取当前节点信息
     */
    ClusterNodeInfo getSelf();

    /**
     * 注册自身信息
     */
    void registerSelf();
}
