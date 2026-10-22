package com.sfc.cloud.task.worker.annotation;

import com.sfc.cloud.task.worker.SfcTaskWorkerAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用咸鱼云微服务任务执行者（Worker）能力。
 *
 * <p>标注在 Spring Boot 启动类上后，本服务会：</p>
 * <ol>
 *     <li>自动扫描容器中的 {@link com.sfc.cloud.task.worker.SfcTaskHandler} Bean 并注册支持的任务类型；</li>
 *     <li>向 sfc-core 注册自身（{@code /api/internal/task/worker/register}）并按心跳周期续约；</li>
 *     <li>长轮询认领任务、执行、上报进度/日志/结果；</li>
 *     <li>暴露中断端点 {@code POST /api/internal/task/interrupt}，供 core 下发中断指令。</li>
 * </ol>
 *
 * <p>⚠️ 中断端点属于 {@code /api/internal/**} 内部接口：消费方必须同时开启
 * {@code @EnableSfcInternalAuth} 并配置与服务端一致的 {@code sfc.internal.key}
 * （或环境变量 {@code SFC_INTERNAL_KEY}），否则 core 的内部控制指令将无法通过鉴权。</p>
 *
 * <p>能力开关与参数见配置项前缀 {@code sfc.task.worker}。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(SfcTaskWorkerAutoConfiguration.class)
public @interface EnableSfcTaskWorker {
}
