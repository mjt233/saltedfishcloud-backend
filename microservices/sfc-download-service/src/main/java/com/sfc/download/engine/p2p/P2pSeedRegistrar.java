package com.sfc.download.engine.p2p;

import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.model.DownloadTaskParams;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 做种保留 SPI：由做种生命周期实现（做种阶段）提供，下载接管阶段（本阶段）只依赖本接口。
 *
 * <p><b>为什么是 SPI：</b>下载接管（检查点 / 续传 / 收口）与做种生命周期（登记 / 到期清理 / 同 infohash
 * 复用）是两个独立演进的能力。接管阶段通过 {@code ObjectProvider<P2pSeedRegistrar>} 懒加载本接口，
 * 实现缺失时使用 no-op 兜底，保证下载服务在未装配做种实现时仍可独立编译与运行。</p>
 *
 * <p><b>实现约定：</b></p>
 * <ul>
 *   <li>{@link #register} 在下载完成、上传之前调用；实现应把做种信息回写到传入的 {@link P2pTaskState}
 *       （{@code seeding / seedTaskId / seedEndAt}），协调器在调用返回后统一保存检查点；</li>
 *   <li>{@link #findReusable} 只做查询，不得产生副作用；查询失败应抛出异常或返回空，由调用方降级；</li>
 *   <li>{@link #isProtectedDirectory} 用于陈旧目录清理与 aria2 孤儿任务对账的做种保护判断；
 *       <b>查询失败必须抛出异常</b>，由调用方按 fail-safe 跳过本轮清理（不能把异常吞成 {@code false}，
 *       否则可能误删做种中的文件）。</li>
 * </ul>
 */
public interface P2pSeedRegistrar {

    /**
     * 下载完成时登记做种保留。
     *
     * <p>实现读取统一配置 {@code download.aria2.seed-time-minutes}：{@code <= 0} 表示不做种，直接返回
     * {@code false}（调用方据此走「完成即清理」收口）；否则写入做种记录并把
     * {@code seeding / seedTaskId / seedEndAt} 回写到 {@code state} 后返回 {@code true}。</p>
     *
     * @param context   任务执行上下文（提供日志、任务参数与检查点保存能力）
     * @param state     当前检查点（实现可就地回写做种字段）
     * @param artifacts 本次下载产物列表，允许为空
     * @return 登记成功且需要保留本地目录做种时返回 {@code true}
     */
    boolean register(DownloadContext context, P2pTaskState state, List<DownloadArtifact> artifacts);

    /**
     * 查询可复用的做种文件。
     *
     * <p>磁力链按 infohash、种子任务按种子内容 SHA-256 匹配状态为「做种中」、未到期且本地目录存在的
     * 记录。命中后调用方不再提交 aria2，直接读取现有文件上传。</p>
     *
     * @param params 下载任务参数
     * @return 可复用目标；未命中或无法判断时返回 {@link Optional#empty()}
     */
    Optional<P2pSeedReuseTarget> findReusable(DownloadTaskParams params);

    /**
     * 判断目录是否处于做种保护中（禁止被陈旧清理 / 孤儿对账删除）。
     *
     * @param dir 待判断的本服务侧目录，允许为 {@code null}
     * @return 该目录被做种记录保护时返回 {@code true}
     */
    boolean isProtectedDirectory(Path dir);
}
