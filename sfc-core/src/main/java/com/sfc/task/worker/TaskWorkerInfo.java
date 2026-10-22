package com.sfc.task.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 任务执行者（worker）注册信息。
 * <p>每个 core 节点与每个接入异步任务系统的微服务实例都会注册一份，注册信息保存在缓存中并
 * 通过周期性重新注册（心跳）续约。健康检查通过该信息判断任务执行者是否存活，调度时通过
 * {@link #types} 判断执行者是否具备执行某类任务的能力。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskWorkerInfo {
    /**
     * worker 唯一标识。core 节点为 {@code node-{节点id}}，微服务为实例启动时生成或配置的标识。
     */
    private String workerId;

    /**
     * worker 类型
     */
    private TaskWorkerKind kind;

    /**
     * 所属服务名。core 节点为 {@code sfc-core}，微服务为 {@code spring.application.name}
     */
    private String serviceName;

    /**
     * 回调地址（不含路径），如 {@code http://127.0.0.1:8090}。<br>
     * 供 core 主动向 worker 下发中断等控制指令使用。core 节点自身为空（通过集群 RPC 通信）。
     */
    private String callbackUrl;

    /**
     * 该 worker 支持执行的任务类型集合
     */
    private List<String> types;

    /**
     * 该 worker 的最大负载。取值范围与任务 CPU 开销一致：100 表示 1 个 CPU 核心的算力。
     */
    private Integer maxLoad;

    /**
     * 该 worker 当前负载
     */
    private Integer currentLoad;

    /**
     * 最后一次注册（心跳）时间
     */
    private Date lastHeartbeat;
}
