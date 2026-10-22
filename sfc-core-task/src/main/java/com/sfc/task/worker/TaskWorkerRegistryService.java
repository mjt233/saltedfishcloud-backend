package com.sfc.task.worker;

import com.sfc.cloud.data.id.SfcIdUtil;
import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行者（worker）注册中心。
 * <p>执行者在缓存中注册自身信息（支持的任务类型、负载、回调地址等），并通过周期性重新注册
 * （心跳）续约。本注册中心是「任务类型路由调度」的能力数据来源：</p>
 * <ul>
 *     <li>任务只会被派发给声明支持该任务类型的执行者；</li>
 *     <li>执行者注册信息过期即视为失联，其名下的运行中任务会被健康检查回收重发。</li>
 * </ul>
 */
@Slf4j
@Component
public class TaskWorkerRegistryService {
    private static final String LOG_PREFIX = "[异步任务Worker注册]";

    /**
     * worker 注册信息的有效期（秒）。worker 的重新注册（心跳）周期应明显小于该值。
     */
    public static final int WORKER_TTL_SECONDS = 30;

    /**
     * 本地 core 节点的服务名，读取不到 {@code spring.application.name} 时使用。
     */
    private static final String DEFAULT_LOCAL_SERVICE_NAME = "sfc-core";

    private final CacheService cacheService;

    /**
     * 当前 core 节点作为 worker 的标识
     */
    private final String localWorkerId;

    /**
     * 当前 core 节点的服务名
     */
    private final String localServiceName;

    public TaskWorkerRegistryService(CacheService cacheService, Environment environment) {
        this.cacheService = cacheService;
        this.localWorkerId = "node-" + SfcIdUtil.getId();
        this.localServiceName = Optional.ofNullable(environment.getProperty("spring.application.name"))
                .filter(StringUtils::hasText)
                .orElse(DEFAULT_LOCAL_SERVICE_NAME);
    }

    /**
     * 注册或续约一个 worker。
     *
     * @param info worker 注册信息
     */
    public void register(TaskWorkerInfo info) {
        if (info == null || !StringUtils.hasText(info.getWorkerId())) {
            throw new IllegalArgumentException("workerId 不能为空");
        }
        info.setLastHeartbeat(new Date());
        cacheService.set(getWorkerKey(info.getWorkerId()), info, WORKER_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("{}注册worker: {}({})", LOG_PREFIX, info.getWorkerId(), info.getServiceName());
    }

    /**
     * 从注册信息构建并注册一个 worker。
     *
     * @param workerId    worker 标识
     * @param kind        worker 类型
     * @param serviceName 服务名
     * @param callbackUrl 回调地址，core 节点为空
     * @param types       支持的任务类型
     * @param maxLoad     最大负载
     * @param currentLoad 当前负载
     */
    public void register(String workerId, TaskWorkerKind kind, String serviceName, String callbackUrl,
                         Collection<String> types, Integer maxLoad, Integer currentLoad) {
        register(TaskWorkerInfo.builder()
                .workerId(workerId)
                .kind(kind)
                .serviceName(serviceName)
                .callbackUrl(callbackUrl)
                .types(types == null ? new ArrayList<>() : new ArrayList<>(types))
                .maxLoad(maxLoad)
                .currentLoad(currentLoad)
                .build());
    }

    /**
     * 注册或续约当前 core 节点（本地 worker）。
     *
     * @param types       本地支持的任务类型
     * @param maxLoad     最大负载
     * @param currentLoad 当前负载
     */
    public void registerLocal(Collection<String> types, int maxLoad, int currentLoad) {
        register(localWorkerId, TaskWorkerKind.LOCAL_NODE, localServiceName, null, types, maxLoad, currentLoad);
    }

    /**
     * 注销一个 worker。
     *
     * @param workerId worker 标识
     */
    public void unregister(String workerId) {
        if (StringUtils.hasText(workerId)) {
            cacheService.delete(getWorkerKey(workerId));
            log.info("{}注销worker: {}", LOG_PREFIX, workerId);
        }
    }

    /**
     * 注销当前 core 节点（本地 worker）。
     */
    public void unregisterLocal() {
        unregister(localWorkerId);
    }

    /**
     * 判断 worker 是否存活（注册信息未过期）。
     *
     * @param workerId worker 标识
     * @return 存活返回 true
     */
    public boolean isAlive(String workerId) {
        return StringUtils.hasText(workerId) && cacheService.hasKey(getWorkerKey(workerId));
    }

    /**
     * 获取 worker 注册信息。
     *
     * @param workerId worker 标识
     * @return 注册信息，不存在或已过期时返回 null
     */
    public TaskWorkerInfo get(String workerId) {
        if (!StringUtils.hasText(workerId)) {
            return null;
        }
        return cacheService.get(getWorkerKey(workerId));
    }

    /**
     * 获取全部存活 worker 的注册信息。
     *
     * @return worker 注册信息列表
     */
    public List<TaskWorkerInfo> list() {
        Set<String> keys = cacheService.scanKeys(CacheKeyPrefixes.TASK_WORKER + "*");
        List<TaskWorkerInfo> result = new ArrayList<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        for (String key : keys) {
            TaskWorkerInfo info = cacheService.get(key);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 获取当前 core 节点作为 worker 的标识。
     *
     * @return 本地 worker 标识
     */
    public String getLocalWorkerId() {
        return localWorkerId;
    }

    private String getWorkerKey(String workerId) {
        return CacheKeyPrefixes.TASK_WORKER + workerId;
    }
}
