package com.sfc.download.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下载任务关联的异步任务引用（状态镜像的最小快照）。
 *
 * <p>旧插件通过 JPA 关联直接读取宿主 {@code AsyncTaskRecord}；迁为独立微服务后本服务没有该实体，
 * 列表接口改为经 {@code SfcTaskWorkerClient} 批量查询任务状态/进度，并以此 VO 交给前端，
 * 保持前端读取 {@code asyncTaskRecord.status} 的契约不变。</p>
 *
 * <p>该对象是 {@link com.sfc.download.model.po.DownloadTaskInfo} 的 {@code @Transient} 字段，
 * 不参与持久化。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskRef {

    /**
     * 异步任务 id（core 侧的 {@code AsyncTaskRecord.id}）。
     */
    private Long id;

    /**
     * 异步任务状态，取值见 core 的 {@code AsyncTaskConstants.Status}：
     * {@code 0} 等待、{@code 1} 运行中、{@code 2} 完成、{@code 3} 失败、{@code 4} 取消、{@code 5} 离线。
     */
    private Integer status;
}
