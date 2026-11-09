package com.sfc.download.repo;

import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.sfc.download.model.po.DownloadSeedTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 做种保留记录仓储。
 *
 * <p>常规查询与批量操作继承自 {@link SfcBaseRepo}；本接口只补充做种生命周期特有的查询与回写方法：</p>
 * <ul>
 *   <li>到期扫描：{@link #findExpired}；</li>
 *   <li>并发抢占：{@link #claimExpired}（{@code status 0 → 1}，多实例部署下只有一个实例能抢到）；</li>
 *   <li>同 infohash / 同种子复用查询：{@link #findActiveByInfohash}、{@link #findByTorrentSha256}；</li>
 *   <li>列表展示与目录保护：{@link #findByDownloadIdInAndStatusIn}、{@link #existsByStatusAndLocalDir}；</li>
 *   <li>清理终态回写：{@link #updateFinish}、{@link #updateFinishByIds}。</li>
 * </ul>
 *
 * <p><b>为什么回写用 {@code @Modifying} 的 JPQL：</b>定时清理与任务执行线程可能并发操作同一条记录，
 * 「先查后改」会带来覆盖问题；同时批量回写可以避免逐条更新数据库。所有批量更新方法都会显式刷新
 * {@code update_at}，因为 JPQL 批量更新不会触发 JPA 审计监听器，而「清理中超过 10 分钟」的重试判定依赖该列。</p>
 *
 * <p><b>分页约定：</b>带 {@link Pageable} 的查询均返回 {@link Page}（项目规范），到期扫描用分页限制单轮
 * 处理量，避免一次加载过多记录。</p>
 */
public interface DownloadSeedTaskRepo extends SfcBaseRepo<DownloadSeedTask> {

    /**
     * 分页查询已到期的做种记录。
     *
     * @param status   期望的状态（传 {@link com.sfc.download.seed.SeedTaskStatus#SEEDING} 表示做种中）
     * @param now      当前时间；{@code seed_end_at <= now} 视为到期
     * @param pageable 分页参数（用于限制单轮处理数量）
     * @return 按到期时间升序排列的分页结果（最早到期的先清理）
     */
    @Query("SELECT t FROM DownloadSeedTask t WHERE t.status = :status AND t.seedEndAt <= :now "
            + "ORDER BY t.seedEndAt ASC")
    Page<DownloadSeedTask> findExpired(@Param("status") Integer status,
                                       @Param("now") Date now,
                                       Pageable pageable);

    /**
     * 抢占一条到期做种记录（原子比较并设置）。
     *
     * <p>只有 {@code status} 仍等于期望值（做种中）时才更新为清理中，返回受影响行数。多实例部署时，
     * 返回 0 表示已被其它实例抢先处理，调用方应跳过该记录。</p>
     *
     * @param id             做种记录 id
     * @param expectedStatus 期望的原状态（通常为做种中）
     * @param claimedStatus  抢占后的状态（通常为清理中）
     * @param claimedAt      抢占时间（同时刷新 {@code update_at}，供「清理中超时重试」判定）
     * @return 实际更新行数（1=抢占成功，0=状态已变化）
     */
    @Modifying
    @Transactional
    @Query("UPDATE DownloadSeedTask t SET t.status = :claimedStatus, t.updateAt = :claimedAt "
            + "WHERE t.id = :id AND t.status = :expectedStatus")
    int claimExpired(@Param("id") Long id,
                     @Param("expectedStatus") Integer expectedStatus,
                     @Param("claimedStatus") Integer claimedStatus,
                     @Param("claimedAt") Date claimedAt);

    /**
     * 分页查询「清理中」但更新时间早于阈值的记录。
     *
     * <p>用于回收上次清理中途崩溃（进程退出、机器重启）遗留的记录：按固定时长（10 分钟）判断超时，
     * 重新执行一次幂等清理。</p>
     *
     * @param status    期望状态（传 {@link com.sfc.download.seed.SeedTaskStatus#CLEANING}）
     * @param threshold 超时阈值；{@code update_at < threshold} 视为超时
     * @param pageable  分页参数（用于限制单轮处理数量）
     * @return 分页结果（按更新时间升序，优先处理最久未推进的记录）
     */
    @Query("SELECT t FROM DownloadSeedTask t WHERE t.status = :status AND t.updateAt < :threshold "
            + "ORDER BY t.updateAt ASC")
    Page<DownloadSeedTask> findByStatusAndUpdateAtBefore(@Param("status") Integer status,
                                                         @Param("threshold") Date threshold,
                                                         Pageable pageable);

    /**
     * 查询指定 infohash 下仍然生效（未到期）的做种记录。
     *
     * @param infohash 小写 hex 的 infohash
     * @param status   做种状态（通常为做种中）
     * @param now      当前时间；{@code seed_end_at > now} 表示未到期
     * @return 命中的做种记录（多个时按到期时间倒序；正常情况下最多一条）
     */
    @Query("SELECT t FROM DownloadSeedTask t WHERE t.infohash = :infohash AND t.status = :status "
            + "AND t.seedEndAt > :now ORDER BY t.seedEndAt DESC")
    List<DownloadSeedTask> findActiveByInfohash(@Param("infohash") String infohash,
                                                @Param("status") Integer status,
                                                @Param("now") Date now);

    /**
     * 查询指定种子内容 SHA-256 下仍然生效（未到期）的做种记录。
     *
     * @param torrentSha256 种子内容的 SHA-256（小写 hex）
     * @param status        做种状态（通常为做种中）
     * @param now           当前时间；{@code seed_end_at > now} 表示未到期
     * @return 命中的做种记录（多个时按到期时间倒序）
     */
    @Query("SELECT t FROM DownloadSeedTask t WHERE t.torrentSha256 = :torrentSha256 "
            + "AND t.status = :status AND t.seedEndAt > :now ORDER BY t.seedEndAt DESC")
    List<DownloadSeedTask> findByTorrentSha256(@Param("torrentSha256") String torrentSha256,
                                               @Param("status") Integer status,
                                               @Param("now") Date now);

    /**
     * 按下载任务 id 集合批量查询指定状态的做种记录。
     *
     * <p>用于任务列表批量填充 transient 展示字段；调用方应按
     * {@link com.sfc.download.seed.SeedTaskBatchUtils#partition} 分批，避免单条 SQL 参数过多。</p>
     *
     * @param downloadIds 下载任务 id 集合
     * @param statuses    做种记录状态集合
     * @return 命中的做种记录
     */
    @Query("SELECT t FROM DownloadSeedTask t WHERE t.downloadId IN :downloadIds AND t.status IN :statuses")
    List<DownloadSeedTask> findByDownloadIdInAndStatusIn(@Param("downloadIds") Collection<Long> downloadIds,
                                                         @Param("statuses") Collection<Integer> statuses);

    /**
     * 按下载任务 id 查询做种记录（{@code download_id} 唯一）。
     *
     * @param downloadId 下载任务 id
     * @return 做种记录；不存在时返回 {@link Optional#empty()}
     */
    Optional<DownloadSeedTask> findByDownloadId(Long downloadId);

    /**
     * 查询全部生效中的做种记录对应的本服务侧目录。
     *
     * <p>供目录保护判断做兜底比对使用：精确匹配未命中时，一次性拉取生效中的做种目录，按规范化路径逐一比对，
     * 兼容登记录入的目录文本与清理侧传入路径写法不同的情况。</p>
     *
     * @param status 做种记录状态（通常为做种中）
     * @return 本服务侧目录字符串列表（已过滤空值）
     */
    @Query("SELECT t.localDir FROM DownloadSeedTask t WHERE t.status = :status "
            + "AND t.localDir IS NOT NULL AND t.localDir <> ''")
    List<String> findAllActiveLocalDirs(@Param("status") Integer status);

    /**
     * 判断指定的本服务侧目录是否被某个状态的做种记录占用。
     *
     * @param status   做种记录状态（通常为做种中）
     * @param localDir 本服务侧目录字符串（与登记时写入的格式一致）
     * @return 存在对应记录返回 {@code true}
     */
    boolean existsByStatusAndLocalDir(Integer status, String localDir);

    /**
     * 回写单条做种记录的终态（已清理 / 异常）。
     *
     * @param id       做种记录 id
     * @param status   终态状态（{@link com.sfc.download.seed.SeedTaskStatus#CLEANED} 或
     *                 {@link com.sfc.download.seed.SeedTaskStatus#ERROR}）
     * @param message  清理结果或异常信息，允许为 {@code null}
     * @param finishAt 进入终态的时间（同时刷新 {@code update_at}）
     */
    @Modifying
    @Transactional
    @Query("UPDATE DownloadSeedTask t SET t.status = :status, t.message = :message, "
            + "t.finishAt = :finishAt, t.updateAt = :finishAt WHERE t.id = :id")
    void updateFinish(@Param("id") Long id,
                      @Param("status") Integer status,
                      @Param("message") String message,
                      @Param("finishAt") Date finishAt);

    /**
     * 批量回写多条做种记录的终态。
     *
     * <p>用于一轮扫描中清理成功的记录批量落终态；调用方应按
     * {@link com.sfc.download.seed.SeedTaskBatchUtils#partition} 分批。所有记录共用同一条 message。</p>
     *
     * @param ids      做种记录 id 集合
     * @param status   终态状态
     * @param message  统一的清理结果信息
     * @param finishAt 进入终态的时间（同时刷新 {@code update_at}）
     * @return 实际更新行数
     */
    @Modifying
    @Transactional
    @Query("UPDATE DownloadSeedTask t SET t.status = :status, t.message = :message, "
            + "t.finishAt = :finishAt, t.updateAt = :finishAt WHERE t.id IN :ids")
    int updateFinishByIds(@Param("ids") Collection<Long> ids,
                          @Param("status") Integer status,
                          @Param("message") String message,
                          @Param("finishAt") Date finishAt);
}
