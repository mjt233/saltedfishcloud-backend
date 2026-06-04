package com.sfc.pxeboot.service;

import com.sfc.pxeboot.model.dto.PxeSessionInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PXE 会话追踪器
 * 追踪活跃的 PXE 启动会话
 */
@Slf4j
public class PxeSessionTracker {

    private static final long CLEANUP_INTERVAL_MINUTES = 5;
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;

    private final Map<String, PxeSessionInfo> sessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    @PostConstruct
    public void init() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PXE-Session-Cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupStaleTask, CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
        log.debug("[PXE-Session] 会话清理任务已启动，间隔: {} 分钟", CLEANUP_INTERVAL_MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
        }
    }

    private void cleanupStaleTask() {
        cleanupStale(SESSION_TIMEOUT_MS);
        log.debug("[PXE-Session] 会话清理完成，当前活跃会话数: {}", sessions.size());
    }

    /**
     * 记录客户端请求
     */
    public void recordRequest(String clientIp, String path, long bytesTransferred) {
        PxeSessionInfo info = sessions.computeIfAbsent(clientIp, k -> new PxeSessionInfo());
        info.setClientIp(clientIp);
        info.setLastRequestPath(path);
        info.setLastActiveTime(System.currentTimeMillis());
        info.setTotalBytesTransferred(info.getTotalBytesTransferred() + bytesTransferred);
        info.setRequestCount(info.getRequestCount() + 1);
    }

    /**
     * 获取所有活跃会话
     */
    public List<PxeSessionInfo> getActiveSessions() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * 清理过期会话
     */
    public void cleanupStale(long timeoutMs) {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry ->
            now - entry.getValue().getLastActiveTime() > timeoutMs
        );
    }
}
