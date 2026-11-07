package com.sfc.task.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务执行者（worker）视图信息。
 *
 * <p>由服务发现中的实例信息组装，用于管理接口展示可参与任务执行的服务实例；
 * {@link #local} 为 true 表示当前 core 节点自身，此时才会给出其支持的任务类型。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskWorkerInfo {
    /**
     * 所属服务名（服务注册中心中的 serviceId）
     */
    private String serviceId;

    /**
     * 实例地址（host:port），与任务记录中的 {@code claimedWorker} 一致
     */
    private String workerId;

    /**
     * 执行者角色，取值见 {@link com.sfc.task.AsyncTaskConstants.WorkerRole}；远程实例为空
     */
    private String role;

    /**
     * 实例主机地址
     */
    private String host;

    /**
     * 实例端口
     */
    private int port;

    /**
     * 是否使用 HTTPS
     */
    private boolean secure;

    /**
     * 是否为当前 core 节点自身
     */
    private boolean local;

    /**
     * 支持执行的任务类型集合；仅当前节点自身有值
     */
    private List<String> types;
}
