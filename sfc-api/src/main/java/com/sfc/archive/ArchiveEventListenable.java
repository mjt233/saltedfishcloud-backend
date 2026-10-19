package com.sfc.archive;

/**
 * 压缩/解压缩事件提供者接口
 */
public interface ArchiveEventListenable {
    /**
     * 添加事件监听器
     * @param listener  监听器
     */
    void addEventListener(ArchiveHandleEventListener listener);
}
