package com.sfc.download.service;

import com.sfc.cloud.model.SfcProxyInfo;
import com.sfc.download.engine.DownloadCapability;
import com.sfc.download.engine.DownloadType;
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
 *   <li>创建任务时按<b>调用方显式指定</b>的下载类型做统一校验（类型 / 能力 / 来源协议 / 引擎参数），
 *       再校验保存目录与代理，落库本服务的 {@code download_task} 表并向 core 提交异步任务；</li>
 *   <li>提供按下载地址推断下载类型的查询能力，供前端在用户修改 URL 后自动选型；</li>
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
     * <p>流程：统一校验（类型必填 / 能力 / 来源协议 / 引擎参数）→ 校验保存路径与代理配置 →
     * 写入本服务的下载任务表（状态等待中）→ 向 core 提交异步任务 → 回写异步任务 id。
     * 提交失败时会把任务标记为失败并抛出异常。</p>
     *
     * <p>其中「统一校验」必须早于任何 core 调用，以保证「引擎未接入 / 外部程序未启用」这类失败
     * 返回的是一手的错误原因，而不是被保存路径校验掩盖过的二手错误。</p>
     *
     * @param params  下载参数（{@code type} 必填且由调用方指定，服务端不做推导；
     *                {@code downloadId} 由本方法回填，客户端传入无效）
     * @param creator 任务创建者 uid（用于记录操作人与权限校验，与 {@code params.uid} 的资源归属人可能不同）
     * @return 新建的下载任务 id（字符串形式，避免前端 64 位精度丢失）
     */
    String createTask(DownloadTaskParams params, long creator);

    /**
     * 按下载地址推断下载类型。
     *
     * <p>纯语法推断，不发起任何网络请求、不读写配置；判定由各下载引擎的
     * {@link com.sfc.download.engine.DownloadEngine#matchesSource(DownloadType, String)} 承担。
     * 结果只作为前端选择类型的建议：是否存在可用引擎、来源是否合法，仍由创建任务阶段校验决定。</p>
     *
     * @param url 下载地址，允许为 {@code null} 或空白
     * @return 推断出的下载类型；无法识别时返回 {@code null}
     */
    DownloadType detectType(String url);

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

    /**
     * 查询本服务支持的下载类型及其当前可用性。
     *
     * <p>数据来源是容器中已注册的 {@link com.sfc.download.engine.DownloadEngine}，<b>不访问 core</b>：
     * 即使 core 不可用，前端也应当能拿到类型清单并据此渲染表单。</p>
     *
     * @param refresh 是否在返回前强制刷新统一配置快照；{@code true} 用于「管理员刚在 core 后台改完配置、
     *                需要立即验证」的场景，{@code false} 时走配置缓存（默认最多 5 秒自动生效）。
     *                无论取值如何，引擎自身的外部依赖探活结果另有 30 秒缓存，最多 30 秒后自然更新
     * @return 能力声明列表；无任何引擎被装配时返回空列表
     */
    List<DownloadCapability> listCapabilities(boolean refresh);
}
