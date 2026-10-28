package com.sfc.download.controller;

import com.sfc.cloud.data.validation.SfcUID;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.cloud.model.SfcProxyInfo;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.SfcPageInfo;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.download.engine.DownloadCapability;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.identity.SfcCurrentUserSupport;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.model.param.TaskType;
import com.sfc.download.model.po.DownloadTaskInfo;
import com.sfc.download.service.DownloadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 离线下载控制器。
 *
 * <p>接口路径与旧插件 {@code sfc-ext-download} 保持一致（{@value #PREFIX}），前端只改请求基址即可复用：
 * 新基址为网关动态路由 {@code /api/service/sfc-download-service}。</p>
 *
 * <p><b>身份与权限：</b>本服务没有 Spring Security，当前用户取自网关注入的
 * {@code X-Sfc-Uid} 信任头（见 {@link SfcCurrentUserSupport}）；资源级权限（资源归属人 / 管理员）
 * 由共享的 {@link SfcUIDValidator} 依据身份 SPI 判定。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(DownloadController.PREFIX)
@Validated
public class DownloadController {

    /** 接口前缀，与旧插件保持一致 */
    public static final String PREFIX = "/api/task/download";

    /** 下载任务服务 */
    private final DownloadService downloadService;

    /**
     * 创建一个离线下载任务。
     *
     * @param params 下载参数（资源归属 uid 必须是当前用户本人或公共网盘；写公共网盘需要管理员）
     * @return 新建下载任务的 id（字符串形式，避免前端 64 位精度丢失）
     */
    @PostMapping
    public SfcJsonResult<String> createTask(@RequestBody @Validated DownloadTaskParams params) {
        Long currentUid = SfcCurrentUserSupport.getCurrentUid();
        if (currentUid == null) {
            throw new SfcJsonException(401, "请先登录");
        }
        // 资源级权限：普通用户只能把文件保存到自己或公共（uid=0）网盘，写公共网盘仅管理员
        SfcUIDValidator.validateWithException(params.uid, true);
        return SfcJsonResult.getInstance(downloadService.createTask(params, currentUid));
    }

    /**
     * 分页查询指定用户的下载任务列表。
     *
     * @param uid  任务归属用户 uid（由 {@link SfcUID} 校验当前用户是否有权查看）
     * @param page 页码，从 1 开始
     * @param size 每页数量，取值 5~400
     * @param type 状态筛选类型，默认 {@link TaskType#ALL}
     * @return 分页结果（{@code content/totalCount/totalPage} 结构与宿主一致）
     */
    @GetMapping
    public SfcJsonResult<SfcPageInfo<DownloadTaskInfo>> getAllTask(
            @SfcUID @RequestParam long uid,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(5) @Max(400) int size,
            @RequestParam(defaultValue = "ALL") TaskType type) {
        Page<DownloadTaskInfo> result = downloadService.getTaskList(uid, page - 1, size, type);
        return SfcJsonResult.getInstance(SfcPageInfo.of(result));
    }

    /**
     * 中断（取消）一个下载任务。
     *
     * @param taskId 下载任务 id（字符串形式，与前端契约一致）
     * @param uid    兼容旧前端可能多传的查询参数，服务端不使用
     * @return 空成功响应
     */
    @DeleteMapping
    @SuppressWarnings("unused")
    public SfcJsonResult<Object> interrupt(@RequestParam String taskId,
                                           @RequestParam(required = false) Long uid) {
        downloadService.interrupt(parseTaskId(taskId));
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 查询当前登录用户可用的代理节点列表。
     *
     * <p>可见范围由 core 判定（公共代理 + 本人代理；受保护的公共代理仅管理员可见）；
     * 未登录时返回空列表。</p>
     *
     * @return 代理节点列表
     */
    @GetMapping("proxy")
    public SfcJsonResult<List<SfcProxyInfo>> listAvailableProxy() {
        return SfcJsonResult.getInstance(downloadService.listAvailableProxy());
    }

    /**
     * 查询本服务支持的下载类型与当前可用性。
     *
     * <p>未登录返回 401。{@code refresh=true} 时先强制刷新统一配置快照（用于管理员刚在 core 后台
     * 打开 aria2 后立即验证）；aria2 的连通性探测另有约 30 秒缓存，可能仍需等待其自然过期。</p>
     *
     * @param refresh 是否先强制刷新统一配置快照，默认 false
     * @return 能力列表，每项含 type/name/engineId/available/reason/options
     */
    @GetMapping("capabilities")
    public SfcJsonResult<List<DownloadCapability>> listCapabilities(
            @RequestParam(defaultValue = "false") boolean refresh) {
        if (SfcCurrentUserSupport.getCurrentUid() == null) {
            throw new SfcJsonException(401, "请先登录");
        }
        return SfcJsonResult.getInstance(downloadService.listCapabilities(refresh));
    }

    /**
     * 按下载地址推断下载类型。
     *
     * <p>供前端在用户修改/粘贴下载地址后自动选择「下载类型」（用户仍可手动修改）。推断是纯本地语法判断，
     * 不访问 core、不发起网络请求；结果只作为建议——类型是否存在可用引擎、来源是否合法，仍由创建任务接口
     * 校验。无法识别时返回 {@code data=null}（信封 {@code code=200}），前端应保持当前选择。</p>
     *
     * <p>需登录（与 {@code /capabilities} 一致）：未登录返回信封 {@code code=401}。</p>
     *
     * @param url 下载地址，允许为 {@code null} 或空白
     * @return 下载类型编码（如 {@code "GIT"}）；无法识别时为 {@code null}
     */
    @GetMapping("detect-type")
    public SfcJsonResult<String> detectType(@RequestParam(required = false) String url) {
        if (SfcCurrentUserSupport.getCurrentUid() == null) {
            throw new SfcJsonException(401, "请先登录");
        }
        DownloadType type = downloadService.detectType(url);
        return SfcJsonResult.getInstance(type == null ? null : type.getCode());
    }

    /**
     * 把前端传来的任务 id 字符串解析为数值 id。
     *
     * @param rawTaskId 字符串形式的任务 id
     * @return 数值任务 id
     * @throws SfcJsonException 任务 id 为空或非法时抛出（HTTP 400 语义）
     */
    private Long parseTaskId(String rawTaskId) {
        if (rawTaskId == null || rawTaskId.isBlank()) {
            throw new SfcJsonException(400, "下载任务id不能为空");
        }
        try {
            return Long.parseLong(rawTaskId.trim());
        } catch (NumberFormatException e) {
            throw new SfcJsonException(400, "下载任务id格式错误: " + rawTaskId);
        }
    }
}
