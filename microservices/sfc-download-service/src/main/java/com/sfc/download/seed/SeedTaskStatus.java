package com.sfc.download.seed;

import java.util.List;

/**
 * 做种保留记录的状态常量（对应 {@code download_seed_task.status} 列）。
 *
 * <p>状态机刻意保持最小：</p>
 *
 * <pre>
 * 做种中(0) ──到期被某个实例抢占──► 清理中(1) ──清理成功──► 已清理(2)
 *                                    │
 *                                    └──清理异常（目录删不掉等）──► 异常(3)
 * </pre>
 *
 * <p>「清理中」记录如果超过 10 分钟没有进入终态，视为上次清理中途崩溃，
 * {@link SeedTaskKeeper} 会重新执行一次幂等清理。因此多实例部署时，抢占（0 → 1）是唯一的并发互斥点，
 * 同一时刻只有一个实例负责一条记录的清理。</p>
 */
public interface SeedTaskStatus {

    /** 做种中：本地文件保留，aria2 正在做种。 */
    Integer SEEDING = 0;

    /** 清理中：已被某个实例抢占，正在进行「停止 aria2 → 删除本地目录」。 */
    Integer CLEANING = 1;

    /** 已清理：清理完成（本地文件已删除），做种记录的终态。 */
    Integer CLEANED = 2;

    /** 异常：清理过程失败（如本地目录删除后仍存在），做种记录的终态，需要人工介入。 */
    Integer ERROR = 3;

    /**
     * 视为「做种保留仍然生效」的状态集合（做种中 + 清理中）。
     *
     * <p>用于列表展示字段填充与「目录是否受做种保护」判断：清理中虽然文件即将删除，但在完成前
     * 仍属于做种记录的有效期。</p>
     */
    List<Integer> ACTIVE_STATUSES = List.of(SEEDING, CLEANING);
}
