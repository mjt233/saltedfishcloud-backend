package com.sfc.pxeboot.service;

import com.sfc.pxeboot.model.dto.PxeSessionInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PXE 会话追踪器
 * 追踪活跃的 PXE 启动会话
 */
@Slf4j
public class PxeSessionTracker {

    private final Map<String, PxeSessionInfo> sessions = new ConcurrentHashMap<>();

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
