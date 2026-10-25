package com.sfc.download.service;

import com.sfc.cloud.model.SfcProxyInfo;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.model.param.TaskType;
import com.sfc.download.model.po.DownloadTaskInfo;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 离线下载服务。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>创建任务时校验保存目录与代理，落库本服务的 {@code download_task} 表并向 core 提交异步任务；</li>
 *   <li>列表查询时用 core 的异步任务状态/进度校准本地状态镜像（fail-soft：core 不可用时回退本地镜像）；</li>
 *   <li>中断任务时校验权限与状态后，经 core 的取消能力终止执行；</li>
 *   <li>查询当前用户可见的代理节点（代理归属 core，本服务只做透传）。</li>
 * </ul>
 *
 * <p>真正的下载执行由 {@code com.sfc.download.task} 下的任务处理器完成（不经过本接口）。</p>
 */
public interface DownloadService {

    /**
     * 创建一个离线下载任务。
     *
     * <p>流程：校验保存路径与代理配置 → 写入本服务的下载任务表（状态等待中）→ 向 core 提交异步任务 →
     * 回写异步任务 id。提交失败时会把任务标记为失败并抛出异常。</p>
     *
     * @param params  下载参数（{@code downloadId} 由本方法回填，客户端无需传入）
     * @param creator 任务创建者 uid（用于记录操作人与权限校验，与 {@code params.uid} 的资源归属人可能不同）
     * @return 新建的下载任务 id（字符串形式，避免前端 64 位精度丢失）
     */
    String createTask(DownloadTaskParams params, long creator);

    /**
     * 中断一个下载任务。
     *
     * <p>仅任务创建者或管理员可操作，且仅「等待中 / 运行中」的任务可中断；中断指令经 core 下发到
     * 实际执行该任务的实例。</p>
     *
     * @param taskId 下载任务 id
     */
    void interrupt(Long taskId);

    /**
     * 分页查询指定用户的下载任务列表。
     *
     * <p>查询本地状态镜像后再用 core 的实时状态校准；core 不可用时回退本地镜像（记录的 WARN 日志），
     * 保证列表接口可用。</p>
     *
     * @param uid  任务归属用户 uid
     * @param page 页码，从 0 开始
     * @param size 每页数量
     * @param type 状态筛选类型；为 {@code null} 或 {@link TaskType#ALL} 表示不过滤
     * @return 分页查询结果
     */
    Page<DownloadTaskInfo> getTaskList(long uid, int page, int size, TaskType type);

    /**
     * 查询当前登录用户可见的代理节点列表。
     *
     * <p>可见范围由 core 判定：公共代理 + 本人私有代理（受保护代理仅管理员可见）。
     * 未登录时返回空列表。</p>
     *
     * @return 代理节点列表；未登录或 core 不可用时返回空列表
     */
    List<SfcProxyInfo> listAvailableProxy();
}
