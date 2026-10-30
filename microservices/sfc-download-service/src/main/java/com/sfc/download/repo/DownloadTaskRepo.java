package com.sfc.download.repo;

import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.sfc.download.model.po.DownloadTaskInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;

/**
 * 下载任务仓储。
 *
 * <p>常规查询与批量操作继承自 {@link SfcBaseRepo}（按 uid 分页、按 id 批量查询等）；
 * 本接口只补充离线下载特有的状态筛选与执行期回写方法。</p>
 *
 * <p>回写方法统一使用 {@code @Modifying} 的 JPQL 更新，避免「先查后改」带来的并发覆盖问题
 * （任务执行线程与 API 线程可能同时操作同一行）。</p>
 */
public interface DownloadTaskRepo extends SfcBaseRepo<DownloadTaskInfo> {

    /**
     * 按用户与状态集合分页查询下载任务，按 id 倒序（即创建时间倒序）。
     *
     * @param uid      任务归属人 uid
     * @param status   状态集合（取值见 {@link com.sfc.download.constant.DownloadTaskConstants.Status}）
     * @param pageable 分页参数
     * @return 分页查询结果
     */
    @Query("SELECT t FROM DownloadTaskInfo t WHERE t.uid = :uid AND t.status IN :status ORDER BY t.id DESC")
    Page<DownloadTaskInfo> findByUidAndStatusIn(@Param("uid") Long uid,
                                                @Param("status") Collection<Integer> status,
                                                Pageable pageable);

    /**
     * 回写任务状态镜像。
     *
     * @param id     下载任务 id
     * @param status 异步任务状态（取值见 {@link com.sfc.download.constant.DownloadTaskConstants.Status}）
     */
    @Modifying
    @Transactional
    @Query("UPDATE DownloadTaskInfo t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 回写任务展示名称。
     *
     * <p>执行期由下载引擎解析出名称后立即调用，使任务在运行中即可在列表中展示名称：
     * HTTP 为响应头 / 地址中的文件名，磁力链与种子为下载文件名摘要（规则见
     * {@link com.sfc.download.util.DownloadTaskNameUtils}）。</p>
     *
     * @param id   下载任务 id
     * @param name 任务展示名称
     */
    @Modifying
    @Transactional
    @Query("UPDATE DownloadTaskInfo t SET t.name = :name WHERE t.id = :id")
    void updateName(@Param("id") Long id, @Param("name") String name);

    /**
     * 回写下载文件的大小与文件名（开始接收响应后即可获知）。
     *
     * @param id   下载任务 id
     * @param size 文件总字节数
     * @param name 解析出的文件名
     */
    @Modifying
    @Transactional
    @Query("UPDATE DownloadTaskInfo t SET t.size = :size, t.name = :name WHERE t.id = :id")
    void updateSizeAndName(@Param("id") Long id, @Param("size") Long size, @Param("name") String name);

    /**
     * 回写任务完成状态。
     *
     * @param id       下载任务 id
     * @param status   异步任务状态（通常为完成）
     * @param size     文件总字节数
     * @param loaded   实际已下载字节数
     * @param name     最终保存的文件名
     * @param finishAt 结束时间
     */
    @Modifying
    @Transactional
    @Query("UPDATE DownloadTaskInfo t SET t.status = :status, t.size = :size, t.loaded = :loaded, "
            + "t.name = :name, t.finishAt = :finishAt WHERE t.id = :id")
    void updateFinish(@Param("id") Long id,
                      @Param("status") Integer status,
                      @Param("size") Long size,
                      @Param("loaded") Long loaded,
                      @Param("name") String name,
                      @Param("finishAt") Date finishAt);

    /**
     * 回写任务失败状态与错误信息。
     *
     * @param id       下载任务 id
     * @param status   异步任务状态（通常为失败）
     * @param message  错误信息
     * @param finishAt 结束时间
     */
    @Modifying
    @Transactional
    @Query("UPDATE DownloadTaskInfo t SET t.status = :status, t.message = :message, "
            + "t.finishAt = :finishAt WHERE t.id = :id")
    void updateFailure(@Param("id") Long id,
                       @Param("status") Integer status,
                       @Param("message") String message,
                       @Param("finishAt") Date finishAt);
}
