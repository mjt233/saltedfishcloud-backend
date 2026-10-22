package com.sfc.cloud.task.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 任务 Worker 配置项，前缀 {@code sfc.task.worker}。
 *
 * <p>启用方式：消费方在启动类标注 {@link com.sfc.cloud.task.worker.annotation.EnableSfcTaskWorker}
 * （或仅依赖本 SDK，自动装配文件同样会生效），然后按需覆盖下列配置项。</p>
 *
 * <p>另一个必配项来自内部接口鉴权 SDK：{@code sfc.internal.key}（环境变量
 * {@code SFC_INTERNAL_KEY}），必须与 sfc-core 一致，否则出站凭证会被 core 拒绝、入站中断指令
 * 也无法通过消费方的 {@code @EnableSfcInternalAuth} 校验。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sfc.task.worker")
public class SfcTaskWorkerProperties {

    /**
     * 是否启用任务 Worker（默认 {@code true}）。
     *
     * <p>设为 {@code false} 时不注册/不认领/不执行任务，也不会暴露中断端点。</p>
     */
    private boolean enabled = true;

    /**
     * sfc-core 的基础地址（默认 {@code http://127.0.0.1:8087}）。
     *
     * <p>所有内部接口调用（注册/认领/上报/提交）都会发往该地址，并自动携带内部调用凭证。</p>
     */
    private String coreBaseUrl = "http://127.0.0.1:8087";

    /**
     * worker 唯一标识。留空时自动生成：{@code 服务名:主机:端口:随机后缀}。
     *
     * <p>同一服务的多个实例必须不同；固定该值可用于在重启后保持任务归属可追踪。</p>
     */
    private String workerId = "";

    /**
     * 所属服务名。留空时取 {@code spring.application.name}，再回退 {@code sfc-task-worker}。
     */
    private String serviceName = "";

    /**
     * 本实例对 core 的回调地址（不含路径），例如 {@code http://127.0.0.1:8090}。
     *
     * <p>core 通过 {@code {callbackBaseUrl}/api/internal/task/interrupt} 下发中断指令。
     * 留空时自动探测：{@code http://本机地址:server.port}（端口取 Web 容器实际监听端口）。</p>
     */
    private String callbackBaseUrl = "";

    /**
     * 最大负载（默认 CPU 核数 * 100，100 表示 1 个 CPU 核心的算力），随心跳上报给 core。
     */
    private int maxLoad = Runtime.getRuntime().availableProcessors() * 100;

    /**
     * 心跳（重新注册）间隔，默认 10 秒。
     *
     * <p>core 侧的 worker 注册信息有效期为 30 秒，本值必须明显小于该有效期，否则会被判定为失联
     * 并触发任务回收。</p>
     */
    private Duration heartbeatInterval = Duration.ofSeconds(10);

    /**
     * 认领任务时无任务可认领的长轮询等待秒数，默认 30 秒。小于等于 0 表示不等待。
     */
    private int claimWaitSeconds = 30;

    /**
     * 最大并发执行任务数，默认 CPU 核数。达到上限后暂停认领，直到有任务结束释放容量。
     */
    private int maxConcurrentTasks = Runtime.getRuntime().availableProcessors();
}
