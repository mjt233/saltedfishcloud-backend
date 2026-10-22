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
 * <p><b>中断语义（尽力而为且幂等）</b>：core 下发的中断指令可能早于执行上下文登记（worker 已认领、
 * 任务线程尚未开始执行）到达。此时中断不会被丢弃，而是先登记到有界的「待处理中断」集合中；
 * 对应任务开始执行时会立即生效。因此 {@link #interrupt(Long)} 对「已认领但未开始执行」的任务
 * 同样返回 true（已受理、稍后生效），并可在任务结束后安全地重复调用。</p>
 *
 * <p>由自动装配创建为单例，任务处理器无需直接使用本类。</p>
 */
@Slf4j
class SfcTaskWorkerRegistry {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 任务类型 → 处理器
     */
    private final Map<String, SfcTaskHandler> handlersByType;

    /**
     * 待处理中断的 taskId 上限。超出后按「最旧优先」淘汰，避免 core 异常重试导致内存泄漏。
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
     * 尚未登记执行上下文的中断指令（插入顺序即「最旧优先」的淘汰顺序）
     */
    private final Set<Long> pendingInterrupts = new LinkedHashSet<>();

    /**
     * 已认领但尚未开始执行的任务占位，避免认领循环在处理器线程启动前超量认领。
     */
    private final AtomicInteger pendingSlots = new AtomicInteger();

    /**
     * 构造注册表，并校验任务类型无重复、无空白。
     *
     * @param handlers   容器中的全部任务处理器
     * @param properties 配置项
     * @throws IllegalStateException 任务类型为空或重复时抛出
     */
    SfcTaskWorkerRegistry(List<SfcTaskHandler> handlers, SfcTaskWorkerProperties properties) {
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
     * 获取支持的任务类型集合（注册给 core 的能力声明）。
     *
     * @return 任务类型集合
     */
    Collection<String> getTaskTypes() {
        return handlersByType.keySet();
    }

    /**
     * 登记一个运行中的任务执行上下文。
     *
     * <p>若该任务在登记前已收到过中断指令（正常的认领→执行之间的窗口），登记后立即生效。</p>
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
     * 尝试占用一个执行容量（认领前调用）。
     *
     * <p>容量口径为「运行中任务数 + 已认领未开始任务数」，保证并发任务数不超过
     * {@code sfc.task.worker.max-concurrent-tasks}。</p>
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
     * <p>已登记执行上下文 → 立即标记中断并触发回调；尚未登记（worker 已认领、执行线程还未开始）→
     * 登记到有界的待处理中断集合，任务开始执行时立即生效。两种情况都返回 true 表示「已受理」，
     * 避免 core 侧把正常的执行前窗口误判为中断失败。</p>
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
     * 获取当前负载：按运行中任务数折算为与 {@code max-load} 同一量纲的开销指数。
     *
     * @return 当前负载，不超过最大负载
     */
    int getCurrentLoad() {
        int maxLoad = Math.max(1, properties.getMaxLoad());
        int maxConcurrent = maxConcurrentTasks();
        int loadPerTask = Math.max(1, maxLoad / maxConcurrent);
        return Math.min(maxLoad, runningExecutions.size() * loadPerTask);
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
