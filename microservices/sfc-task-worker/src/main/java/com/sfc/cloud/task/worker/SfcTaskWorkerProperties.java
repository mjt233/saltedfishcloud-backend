package com.sfc.cloud.task.worker;

import com.sfc.cloud.constants.SfcServiceName;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * 是否启用任务 Worker（默认 {@code true}）；设为 {@code false} 时不认领任务，也不暴露中断端点。
     */
    private boolean enabled = true;

    /**
     * sfc-core 在服务注册中心中的服务名（默认 {@code sfc-core}）。
     *
     * <p>Worker 经 Spring Cloud LoadBalancer 按该服务名从服务发现中选取 core 实例，多实例默认轮询。</p>
     */
    private String coreServiceId = SfcServiceName.CORE;

    /**
     * 本实例作为任务执行者的角色（默认 {@code SERVICE}）。
     *
     * <p>取值与 core 的 {@code AsyncTaskConstants.WorkerRole} 一致：</p>
     * <ul>
     *     <li>{@code CORE}：core 节点内嵌的执行者；</li>
     *     <li>{@code SERVICE}：微服务实例。</li>
     * </ul>
     *
     * <p>角色随任务认领上报，core 据此选择中断指令的下发方式（core 节点走集群 RPC，
     * 微服务实例走 HTTP 回调）；留空时按 {@code SERVICE} 处理。</p>
     */
    private String role = "SERVICE";

    /**
     * 认领任务时无任务可认领的长轮询等待秒数，默认 30 秒。小于等于 0 表示不等待；
     * 实例满载（只认领紧急任务）时 SDK 会把等待秒数自动缩短为不超过 3 秒。
     */
    private int claimWaitSeconds = 30;

    /**
     * 最大并发执行任务数，默认 CPU 核数。普通任务受该上限限制，满载时只认领紧急任务；
     * 紧急任务不受限制，因此实际并发可能短暂超出本值。
     */
    private int maxConcurrentTasks = Runtime.getRuntime().availableProcessors();
}
