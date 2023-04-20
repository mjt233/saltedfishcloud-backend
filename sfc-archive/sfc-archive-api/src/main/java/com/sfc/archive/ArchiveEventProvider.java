package com.sfc.archive;

/**
 * 压缩/解压缩事件提供者接口
 */
public interface ArchiveEventProvider {
    /**
     * 添加事件监听器
     * @param listener  监听器
     */
    void addEventListener(ArchiveHandleEventListener listener);

    /**
     * 移除事件监听器
     * @param listener  监听器
     * @return          被移除的监听器，若未能移除则返回null
     */
    ArchiveHandleEventListener removeEventListener(ArchiveHandleEventListener listener);
}
