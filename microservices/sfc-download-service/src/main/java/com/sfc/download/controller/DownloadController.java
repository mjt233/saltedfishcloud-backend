package com.sfc.download.controller;

import com.sfc.cloud.data.validation.SfcUID;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.cloud.model.SfcProxyInfo;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.SfcPageInfo;
import com.sfc.common.model.json.SfcJsonResult;
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
