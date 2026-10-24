package com.sfc.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 异步任务工厂注册表。
 *
 * <p>维护「任务类型 → 任务工厂」的映射，是任务类型能力的唯一来源：管理接口据此展示当前节点支持的任务类型，
 * task-worker SDK 的认领循环据此向调度中心声明能力。同一任务类型只允许注册一个工厂，重复注册同一实例无副作用。</p>
 */
@Slf4j
@Component
public class AsyncTaskFactoryRegistry {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[异步任务工厂]";

    /**
     * 任务类型 → 任务工厂，按注册顺序保存
     */
    private final Map<String, AsyncTaskFactory> factories = new LinkedHashMap<>();

    /**
     * 注册一个任务工厂。
     *
     * <p>校验规则：任务工厂及其任务类型不能为空；同一工厂实例重复注册是幂等的；
     * 不同工厂注册相同任务类型时抛出 {@link IllegalArgumentException}。</p>
     *
     * @param factory 任务工厂
     * @throws IllegalArgumentException 任务工厂或任务类型为空、或该任务类型已被其他工厂注册时抛出
     */
    public synchronized void register(AsyncTaskFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("任务工厂不能为空");
        }
        String taskType = factory.getTaskType();
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("任务工厂的任务类型不能为空（" + factory.getClass().getName() + "）");
        }
        String normalizedType = taskType.trim();
        AsyncTaskFactory exists = factories.get(normalizedType);
        if (exists == factory) {
            log.debug("{}任务类型 {} 的任务工厂已注册，忽略重复注册", LOG_PREFIX, normalizedType);
            return;
        }
        if (exists != null) {
            throw new IllegalArgumentException("任务类型 " + normalizedType + " 的任务工厂已被注册");
        }
        factories.put(normalizedType, factory);
        log.info("{}注册任务类型: {}", LOG_PREFIX, normalizedType);
    }

    /**
     * 获取已注册的全部任务类型。
     *
     * @return 任务类型集合（不可变快照，按注册顺序）
     */
    public synchronized Set<String> getTaskTypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(factories.keySet()));
    }

    /**
     * 获取已注册的全部任务工厂。
     *
     * @return 任务工厂列表（不可变快照，按注册顺序）
     */
    public synchronized List<AsyncTaskFactory> getFactories() {
        return List.copyOf(factories.values());
    }
}
