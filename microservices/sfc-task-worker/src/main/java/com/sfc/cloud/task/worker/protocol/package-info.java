/**
 * 任务执行者内部协议定义。
 *
 * <p>本包是宿主（sfc-core 内嵌执行者）与微服务执行者<b>共用的唯一协议定义</b>：
 * {@code /api/internal/task/**} 内部接口的请求/响应报文都使用本包中的类型，两端直接复用同一份
 * 类型定义，避免各自维护 DTO 造成字段漂移。</p>
 *
 * <p>执行者身份（服务注册中心中的 serviceId 与实例地址 {@code host:port}）随认领请求
 * {@link com.sfc.cloud.task.worker.protocol.TaskClaimParam} 上报。</p>
 *
 * <p>本包保持零宿主依赖：只使用 JDK 类型与 Jackson 注解，不引用 sfc-api / JPA / 宿主类，
 * 以便宿主与微服务两端都能直接使用。</p>
 */
package com.sfc.cloud.task.worker.protocol;
