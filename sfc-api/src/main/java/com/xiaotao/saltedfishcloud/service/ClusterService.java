package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.RequestParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;

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
     * 根据节点id获取节点对应的信息
     */
    ClusterNodeInfo getNodeById(Long id);

    /**
     * 向指定节点发起HTTP RPC调用
     * @param nodeId    节点id
     * @param param     请求参数
     * @param typeReference 响应对象类型引用
     * @param <T>       响应对象响应体类型
     * @return          响应对象
     */
    <T> ResponseEntity<T> request(Long nodeId, RequestParam param, ParameterizedTypeReference<T> typeReference);

    /**
     * 注册自身信息
     */
    void registerSelf();
}
