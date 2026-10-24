package com.sfc.cloud.task.worker.annotation;

import com.sfc.cloud.task.worker.SfcTaskWorkerAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用咸鱼云任务执行者（Worker）能力。
 *
 * <p>标注在 Spring Boot 启动类上后，本进程会以服务注册中心中的实例身份参与任务调度，并暴露中断端点
 * {@code POST /api/internal/task/interrupt}；能力开关与参数见配置项前缀 {@code sfc.task.worker}。
 * 宿主（sfc-core）与微服务共用同一套执行侧实现。</p>
 *
 * <p>⚠️ 中断端点属于 {@code /api/internal/**} 内部接口：消费方必须同时开启
 * {@code @EnableSfcInternalAuth} 并配置与服务端一致的 {@code sfc.internal.key}
 * （或环境变量 {@code SFC_INTERNAL_KEY}），否则 core 的内部控制指令将无法通过鉴权。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(SfcTaskWorkerAutoConfiguration.class)
public @interface EnableSfcTaskWorker {
}
