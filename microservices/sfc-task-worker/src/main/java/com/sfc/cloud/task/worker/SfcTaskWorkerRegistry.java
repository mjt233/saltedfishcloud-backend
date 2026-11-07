package com.sfc.cloud.task.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker 运行时注册表：保存「任务类型 → 处理器」能力映射与「任务id → 执行上下文」的运行中任务，
 * 并提供并发容量控制与中断入口。
 *
 * <p>中断为尽力而为且幂等：{@link #interrupt(Long)} 对已认领但尚未开始执行的任务同样返回 true，
 * 任务开始执行时立即生效，并可在任务结束后安全地重复调用。</p>
 *
 * <p>宿主（sfc-core 内嵌执行者）与微服务共用本实现，由自动装配创建为单例；
 * 任务处理器无需直接使用本类。</p>
 */
@Slf4j
public class SfcTaskWorkerRegistry {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 任务类型 → 处理器
     */
    private final Map<String, SfcTaskHandler> handlersByType;

    /**
     * 待处理中断的 taskId 上限，超出后按最旧优先淘汰。
     */
    private static final int MAX_PENDING_INTERRUPTS = 512;

    /**
     * 配置项
     */
    private final SfcTaskWorkerProperties properties;

    /**
     * 运行中任务：任务id → 执行上下文
     */
    private final Map<Long, SfcTaskExecution> runningExecutions = new ConcurrentHashMap<>();

    /**
     * 尚未登记执行上下文的中断指令
     */
    private final Set<Long> pendingInterrupts = new LinkedHashSet<>();

    /**
     * 已认领但尚未开始执行的任务占位数
     */
    private final AtomicInteger pendingSlots = new AtomicInteger();

    /**
     * 构造注册表，并校验任务类型无重复、无空白。
     *
     * @param handlers   容器中的全部任务处理器
     * @param properties 配置项
     * @throws IllegalStateException 任务类型为空或重复时抛出
     */
    public SfcTaskWorkerRegistry(List<SfcTaskHandler> handlers, SfcTaskWorkerProperties properties) {
        this.properties = properties;
        Map<String, SfcTaskHandler> map = new LinkedHashMap<>();
        if (handlers != null) {
            for (SfcTaskHandler handler : handlers) {
                if (handler == null) {
                    continue;
                }
                String taskType = handler.taskType();
                if (!StringUtils.hasText(taskType)) {
                    throw new IllegalStateException("SfcTaskHandler[" + handler.getClass().getName()
                            + "] 的 taskType() 不能为空");
                }
                String normalizedType = taskType.trim();
                SfcTaskHandler exists = map.putIfAbsent(normalizedType, handler);
                if (exists != null) {
                    throw new IllegalStateException("存在重复的任务类型处理器: " + normalizedType
                            + "（" + exists.getClass().getName() + " 与 " + handler.getClass().getName() + "）");
                }
            }
        }
        this.handlersByType = Map.copyOf(map);
        if (this.handlersByType.isEmpty()) {
            log.warn("{}未发现任何 SfcTaskHandler Bean，本实例不会认领任何任务", LOG_PREFIX);
        } else {
            log.info("{}已注册任务类型: {}", LOG_PREFIX, this.handlersByType.keySet());
        }
    }

    /**
     * 按任务类型查找处理器。
     *
     * @param taskType 任务类型
     * @return 处理器，不存在时返回 null
     */
    SfcTaskHandler getHandler(String taskType) {
        return taskType == null ? null : handlersByType.get(taskType);
    }

    /**
     * 判断是否存在可处理的任务类型。
     *
     * @return 至少存在一个处理器时返回 true
     */
    boolean hasHandlers() {
        return !handlersByType.isEmpty();
    }

    /**
     * 获取支持的任务类型集合（认领任务时作为类型过滤条件提交给 core）。
     *
     * @return 任务类型集合
     */
    Collection<String> getTaskTypes() {
        return handlersByType.keySet();
    }

    /**
     * 登记一个运行中的任务执行上下文。
     *
     * <p>若该任务在登记前已收到中断指令，登记后立即生效。</p>
     *
     * @param execution 执行上下文
     */
    void registerExecution(SfcTaskExecution execution) {
        Long taskId = execution.getTaskId();
        runningExecutions.put(taskId, execution);
        boolean pending;
        synchronized (pendingInterrupts) {
            pending = pendingInterrupts.remove(taskId);
        }
        if (pending) {
            log.info("{}任务[{}]开始执行前已收到中断指令，立即生效", LOG_PREFIX, taskId);
            execution.requestInterrupt();
        }
    }

    /**
     * 移除一个已结束的任务执行上下文，并清理其可能残留的待处理中断。
     *
     * @param taskId 任务id
     */
    void unregisterExecution(Long taskId) {
        if (taskId == null) {
            return;
        }
        runningExecutions.remove(taskId);
        synchronized (pendingInterrupts) {
            pendingInterrupts.remove(taskId);
        }
    }

    /**
     * 获取当前运行中的全部执行上下文快照。
     *
     * @return 执行上下文列表
     */
    List<SfcTaskExecution> getRunningExecutions() {
        return new ArrayList<>(runningExecutions.values());
    }

    /**
     * 判断当前是否还有执行容量（运行中任务数与已认领未开始任务数之和小于
     * {@code max-concurrent-tasks}）。
     *
     * @return 还有空闲容量返回 true
     */
    boolean hasCapacity() {
        return runningExecutions.size() + pendingSlots.get() < maxConcurrentTasks();
    }

    /**
     * 尝试占用一个执行容量（认领前调用）。
     *
     * @return 占用成功返回 true；已满载返回 false
     */
    boolean tryAcquireSlot() {
        int maxConcurrent = maxConcurrentTasks();
        while (true) {
            int pending = pendingSlots.get();
            if (runningExecutions.size() + pending >= maxConcurrent) {
                return false;
            }
            if (pendingSlots.compareAndSet(pending, pending + 1)) {
                return true;
            }
        }
    }

    /**
     * 释放一个执行容量（任务已开始执行或认领失败/被放弃时调用）。
     */
    void releaseSlot() {
        pendingSlots.updateAndGet(current -> current > 0 ? current - 1 : 0);
    }

    /**
     * 受理一个任务的中断请求（尽力而为且幂等）。
     *
     * @param taskId 任务id
     * @return 已受理返回 true；{@code taskId} 为空返回 false
     */
    boolean interrupt(Long taskId) {
        if (taskId == null) {
            return false;
        }
        SfcTaskExecution execution = runningExecutions.get(taskId);
        if (execution != null) {
            execution.requestInterrupt();
            return true;
        }

        // 尚未登记：先暂存中断指令
        synchronized (pendingInterrupts) {
            pendingInterrupts.remove(taskId);
            pendingInterrupts.add(taskId);
            while (pendingInterrupts.size() > MAX_PENDING_INTERRUPTS) {
                Iterator<Long> iterator = pendingInterrupts.iterator();
                iterator.next();
                iterator.remove();
            }
        }

        // 双检：中断与登记可能并发，登记完成后再次确认，避免漏掉刚刚登记的执行上下文
        execution = runningExecutions.get(taskId);
        if (execution != null) {
            synchronized (pendingInterrupts) {
                pendingInterrupts.remove(taskId);
            }
            execution.requestInterrupt();
        } else {
            log.info("{}任务[{}]尚未开始执行，已暂存中断指令，开始执行时立即生效", LOG_PREFIX, taskId);
        }
        return true;
    }

    /**
     * 获取生效的最大并发任务数（配置值小于 1 时按 1 处理）。
     *
     * @return 最大并发任务数
     */
    private int maxConcurrentTasks() {
        return Math.max(1, properties.getMaxConcurrentTasks());
    }
}
