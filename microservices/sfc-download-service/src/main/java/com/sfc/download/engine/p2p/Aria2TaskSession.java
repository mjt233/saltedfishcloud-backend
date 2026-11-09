package com.sfc.download.engine.p2p;

import com.sfc.common.utils.SfcStringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单次 aria2 下载任务的会话状态。
 *
 * <p>承载本次任务涉及的全部 gid：磁力链会从「元数据 gid」通过 {@code followedBy} 跟随出「内容 gid」，
 * 中断、收尾清理与检查点持久化必须覆盖所有这些 gid，否则仍处于 active 的下载会继续写盘，并导致同一
 * infohash 无法立即重试（aria2 报 {@code InfoHash is already registered}）。</p>
 *
 * <p><b>线程安全：</b>gid 集合使用并发集合——任务线程可能在轮询中新增跟随 gid，而中断回调线程会同时
 * 遍历它执行 {@code forceRemove}；{@link #infohash} 为 volatile。</p>
 */
public final class Aria2TaskSession {

    /**
     * 首次提交返回的 gid（磁力链场景下通常只是元数据 gid；接管场景为检查点中的首个 gid）。
     */
    private final String primaryGid;

    /**
     * 本次任务涉及的全部 gid（并发安全）。
     */
    private final Set<String> gids = ConcurrentHashMap.newKeySet();

    /**
     * 真正产出文件的 gid（磁力链为跟随出的内容 gid；种子任务为提交 gid）。
     */
    private final Set<String> contentGids = ConcurrentHashMap.newKeySet();

    /**
     * torrent infohash（小写 hex），从 aria2 状态 {@code bittorrent.infoHash} 提取；未知时为 {@code null}。
     */
    private volatile String infohash;

    /**
     * 构造会话并登记首个 gid。
     *
     * @param primaryGid 首次提交返回的 gid，不能为空白
     * @throws IllegalArgumentException gid 为空白时抛出
     */
    public Aria2TaskSession(String primaryGid) {
        if (!SfcStringUtils.hasText(primaryGid)) {
            throw new IllegalArgumentException("aria2 会话的首个 gid 不能为空");
        }
        this.primaryGid = primaryGid.trim();
        this.gids.add(this.primaryGid);
    }

    /**
     * 依据检查点中已跟踪的 gid 重建会话（接管场景）。
     *
     * @param trackedGids 检查点记录的全部 gid，允许为空
     * @return 会话；没有可用 gid 时返回 {@code null}
     */
    public static Aria2TaskSession fromTracked(Collection<String> trackedGids) {
        if (trackedGids == null || trackedGids.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>(trackedGids.size());
        for (String gid : trackedGids) {
            if (SfcStringUtils.hasText(gid) && !normalized.contains(gid.trim())) {
                normalized.add(gid.trim());
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        Aria2TaskSession session = new Aria2TaskSession(normalized.getFirst());
        for (int index = 1; index < normalized.size(); index++) {
            session.track(normalized.get(index));
        }
        return session;
    }

    /**
     * 获取首次提交返回的 gid。
     *
     * @return 首个 gid，永不为 {@code null}
     */
    public String primaryGid() {
        return primaryGid;
    }

    /**
     * 获取本次任务涉及的全部 gid（只读视图，遍历安全）。
     *
     * @return gid 集合
     */
    public Set<String> gids() {
        return Collections.unmodifiableSet(gids);
    }

    /**
     * 获取真正产出文件的 gid（只读视图，遍历安全）。
     *
     * @return 内容 gid 集合
     */
    public Set<String> contentGids() {
        return Collections.unmodifiableSet(contentGids);
    }

    /**
     * 获取 torrent infohash。
     *
     * @return infohash；未知时返回 {@code null}
     */
    public String infohash() {
        return infohash;
    }

    /**
     * 更新 torrent infohash（空值忽略）。
     *
     * @param value 新 infohash，允许为 {@code null}
     */
    public void updateInfohash(String value) {
        if (SfcStringUtils.hasText(value)) {
            this.infohash = value.trim();
        }
    }

    /**
     * 登记一个跟随出来的新 gid。
     *
     * @param gid 新 gid，为 {@code null} 时忽略
     */
    public void track(String gid) {
        if (SfcStringUtils.hasText(gid)) {
            gids.add(gid.trim());
        }
    }

    /**
     * 登记一个真正产出文件的内容 gid。
     *
     * @param gid 内容 gid，为 {@code null} 时忽略
     */
    public void trackContentGid(String gid) {
        if (SfcStringUtils.hasText(gid)) {
            contentGids.add(gid.trim());
        }
    }

    /**
     * 取出用于轮询的初始 gid 集合（保持登记顺序）。
     *
     * @return gid 列表
     */
    public List<String> pollTargets() {
        return new ArrayList<>(new LinkedHashSet<>(gids));
    }
}
