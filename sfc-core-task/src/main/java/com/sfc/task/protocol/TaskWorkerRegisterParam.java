package com.sfc.task.protocol;

import java.util.List;

/**
 * worker 注册（心跳）请求参数。
 * <p>注册接口是幂等的：worker 启动时注册一次，之后按心跳周期重复调用以续约并刷新负载与能力信息。
 * 若 worker 的注册信息因长时间未续约而过期，只需再次调用注册接口即可恢复。</p>
 *
 * @param workerId    worker 唯一标识
 * @param kind        worker 类型：{@code LOCAL_NODE} 或 {@code REMOTE_SERVICE}
 * @param serviceName 所属服务名
 * @param callbackUrl 回调地址（不含路径），core 节点为空
 * @param types       支持执行的任务类型集合
 * @param maxLoad     最大负载
 * @param currentLoad 当前负载
 */
public record TaskWorkerRegisterParam(
        String workerId,
        String kind,
        String serviceName,
        String callbackUrl,
        List<String> types,
        Integer maxLoad,
        Integer currentLoad
) {
}
