package com.sfc.cloud.task.worker;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务进度快照。
 *
 * <p>由任务处理器更新，Worker SDK 按约 1 秒的节流周期读取当前快照上报给 sfc-core
 * （速度由 SDK 端根据两次采样的差值计算）。因此本类的读写都必须是线程安全的。</p>
 *
 * <p>约定：{@code total} 为 {@code -1} 表示总量未知（例如流式处理）。</p>
 */
public class SfcTaskProgress {

    /**
     * 已完成量
     */
    private final AtomicLong loaded = new AtomicLong(0L);

    /**
     * 总量，{@code -1} 表示未知
     */
    private final AtomicLong total = new AtomicLong(-1L);

    /**
     * 构造一个已完成量 0、总量未知（-1）的进度对象。
     */
    public SfcTaskProgress() {
    }

    /**
     * 获取已完成量。
     *
     * @return 已完成量
     */
    public long getLoaded() {
        return loaded.get();
    }

    /**
     * 设置已完成量（覆盖式）。
     *
     * @param loaded 已完成量
     */
    public void setLoaded(long loaded) {
        this.loaded.set(Math.max(0L, loaded));
    }

    /**
     * 累加已完成量。
     *
     * @param delta 增量，负值将被忽略（按 0 处理）
     */
    public void addLoaded(long delta) {
        if (delta > 0L) {
            loaded.addAndGet(delta);
        }
    }

    /**
     * 获取总量。
     *
     * @return 总量，{@code -1} 表示未知
     */
    public long getTotal() {
        return total.get();
    }

    /**
     * 设置总量。
     *
     * @param total 总量，{@code -1} 表示未知
     */
    public void setTotal(long total) {
        this.total.set(total < 0L ? -1L : total);
    }
}
