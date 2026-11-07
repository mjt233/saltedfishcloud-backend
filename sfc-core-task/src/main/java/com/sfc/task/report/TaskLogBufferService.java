package com.sfc.task.report;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 运行中任务日志缓冲服务。
 * <p>执行者（core 内嵌 Worker 与微服务实例）执行任务期间，日志会按批上报到 core；core 先把日志暂存在缓存中，
 * 供运行中任务的日志查询接口读取，任务结束时再统一落库。缓冲行数有上限，超出后只保留最新部分。</p>
 */
@Slf4j
@Component
public class TaskLogBufferService {
    /**
     * 单个任务日志缓冲的最大行数
     */
    private static final int MAX_BUFFER_LINES = 2000;

    /**
     * 日志缓冲过期时间（小时）
     */
    private static final long BUFFER_TTL_HOURS = 1;

    private final CacheService cacheService;

    public TaskLogBufferService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 追加日志行到任务日志缓冲。
     *
     * @param taskId 任务id
     * @param lines  日志行
     */
    public void append(Long taskId, List<String> lines) {
        if (taskId == null || lines == null || lines.isEmpty()) {
            return;
        }
        String key = getKey(taskId);
        List<String> buffer = read(taskId);
        buffer.addAll(lines);
        if (buffer.size() > MAX_BUFFER_LINES) {
            buffer = new ArrayList<>(buffer.subList(buffer.size() - MAX_BUFFER_LINES, buffer.size()));
        }
        cacheService.set(key, buffer, BUFFER_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 读取任务日志缓冲。
     *
     * @param taskId 任务id
     * @return 日志行列表，无缓冲时返回空列表
     */
    public List<String> read(Long taskId) {
        if (taskId == null) {
            return new ArrayList<>();
        }
        Object value = cacheService.get(getKey(taskId));
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object o : list) {
                result.add(String.valueOf(o));
            }
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * 清空任务日志缓冲。
     *
     * @param taskId 任务id
     */
    public void clear(Long taskId) {
        if (taskId != null) {
            cacheService.delete(getKey(taskId));
        }
    }

    /**
     * 读取并清空任务日志缓冲。
     *
     * @param taskId 任务id
     * @return 日志行列表
     */
    public List<String> drain(Long taskId) {
        List<String> lines = read(taskId);
        clear(taskId);
        return lines;
    }

    private String getKey(Long taskId) {
        return CacheKeyPrefixes.TASK_LOG + taskId;
    }
}
