package com.sfc.dm.controller;

import com.sfc.dm.constant.DataManagerTaskType;
import com.sfc.dm.model.dto.*;
import com.sfc.dm.model.po.ClaimRecord;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.service.ClaimService;
import com.sfc.dm.service.InvalidDataService;
import com.sfc.dm.service.identify.FileTypeChecker;
import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskCreateParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 失效数据管理控制器
 */
@RestController
@RequestMapping("/api/dataManager/invalidData")
@RequiredArgsConstructor
public class InvalidDataController {
    private final InvalidDataService invalidDataService;
    private final ClaimService claimService;
    private final AsyncTaskManager asyncTaskManager;
    private final AsyncTaskRecordRepo asyncTaskRecordRepo;
    private final FileTypeChecker fileTypeChecker;

    /**
     * 检查是否有进行中的检测或识别任务
     */
    private void checkConcurrentTask() {
        long count = asyncTaskRecordRepo.count(JpaLambdaQueryWrapper.get(AsyncTaskRecord.class)
                .in(AsyncTaskRecord::getTaskType, DataManagerTaskType.INVALID_DATA_DETECT, DataManagerTaskType.FILE_TYPE_CHECK)
                .in(AsyncTaskRecord::getStatus, AsyncTaskConstants.Status.WAITING, AsyncTaskConstants.Status.RUNNING)
                .build());
        if (count > 0) {
            throw new IllegalStateException("已有进行中的检测或识别任务，请等待完成后再发起");
        }
    }

    /**
     * 获取系统当前支持的文件类型识别器信息。
     * 每个Provider可能支持多种类型，结果按类型扁平化返回。
     */
    @GetMapping("providers")
    public JsonResult<List<FileTypeProviderInfo>> listProviders() {
        List<FileTypeProviderInfo> list = fileTypeChecker.getProviders().stream()
                .flatMap(p -> p.getTypeInfoList().stream()
                        .map(typeInfo -> new FileTypeProviderInfo(
                                p.getId() + "#" + typeInfo.getTypeId(),
                                typeInfo.getTypeId(),
                                typeInfo.getTypeName(),
                                p.getSupportedFileExtensions(),
                                typeInfo.getMetadataDefines()
                        )))
                .toList();
        return JsonResultImpl.getInstance(list);
    }

    /**
     * 下载失效数据内容
     */
    @GetMapping("download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        return invalidDataService.getDownloadResource(id);
    }


    // === 管理员操作 ===

    /**
     * 发起失效数据检测（异步任务）
     */
    @PostMapping("detect")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<Long> detect() throws IOException {
        checkConcurrentTask();
        AsyncTaskCreateParam param = new AsyncTaskCreateParam();
        param.setName("失效数据检测");
        param.setTaskType(DataManagerTaskType.INVALID_DATA_DETECT);
        param.setCpuOverhead(50);
        AsyncTaskRecord record = asyncTaskManager.createTask(param);
        return JsonResultImpl.getInstance(record.getId());
    }

    /**
     * 发起文件识别（异步任务）
     *
     * @param param 识别参数，可选。包含：
     *              <ul>
     *                <li>ids - 指定需要识别的失效数据ID列表，不指定则处理所有待处理待识别的记录</li>
     *                <li>reIdentify - 是否重新识别，为true时即使记录无需识别也执行重新识别并覆盖原有结果</li>
     *              </ul>
     * @return 创建成功后的异步任务ID
     */
    @PostMapping("identify")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<Long> identify(@RequestBody(required = false) IdentifyParam param) throws IOException {
        checkConcurrentTask();
        AsyncTaskCreateParam taskParam = new AsyncTaskCreateParam();
        taskParam.setName("文件类型识别");
        taskParam.setTaskType(DataManagerTaskType.FILE_TYPE_CHECK);
        taskParam.setCpuOverhead(50);
        if (param != null && (param.getIds() != null || param.getReIdentify() != null)) {
            taskParam.setParams(MapperHolder.toJson(param));
        }
        AsyncTaskRecord record = asyncTaskManager.createTask(taskParam);
        return JsonResultImpl.getInstance(record.getId());
    }

    /**
     * 查询失效数据列表（分页+筛选）
     *
     * @param filterId 可选，脚本筛选缓存ID。传此值时使用缓存中的筛选结果分页，忽略 query 中的 filterScript 字段。
     */
    @GetMapping("list")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<CommonPageInfo<InvalidDataRecord>> list(
            InvalidDataQuery query,
            @RequestParam(required = false) String filterId,
            PageableRequest pageableRequest) {
        if (filterId != null && !filterId.isBlank()) {
            return JsonResultImpl.getInstance(invalidDataService.listByFilterId(filterId, pageableRequest));
        }
        return JsonResultImpl.getInstance(invalidDataService.list(query, pageableRequest));
    }

    /**
     * 提交脚本筛选，执行全量查询 + Groovy 脚本筛选，缓存结果 ID 列表并返回 filterId。
     * <p>筛选器将执行全表扫描（配合 DB 筛选条件），通过 Groovy 脚本对每条记录求值，<br>
     * 脚本末行表达式为 true 时保留该记录。筛选后的 ID 列表会缓存 {@code filterCacheTtl} 分钟，<br>
     * 随后可通过 {@code GET /list?filterId=xxx&page=0&size=20} 进行分页查询。</p>
     */
    @PostMapping("filter")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<InvalidDataFilterResult> filter(@RequestBody InvalidDataQuery query) {
        return JsonResultImpl.getInstance(invalidDataService.createFilter(query));
    }

    /**
     * 获取失效数据详情
     */
    @GetMapping("detail/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<InvalidDataRecord> detail(@PathVariable Long id) {
        return JsonResultImpl.getInstance(invalidDataService.getDetail(id));
    }

    /**
     * 批量发布为可认领
     */
    @PostMapping("publish")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> publish(@RequestBody List<Long> idList) {
        return JsonResultImpl.getInstance(invalidDataService.publish(idList));
    }

    /**
     * 批量取消发布
     */
    @PostMapping("unpublish")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> unpublish(@RequestBody List<Long> idList) {
        return JsonResultImpl.getInstance(invalidDataService.unpublish(idList));
    }

    /**
     * 批量快速修复
     */
    @PostMapping("quickFix")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> quickFix(@RequestBody List<Long> ids) {
        return JsonResultImpl.getInstance(invalidDataService.quickFix(ids));
    }

    /**
     * 修复所有待处理数据
     */
    @PostMapping("quickFix/all")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> quickFixAll() {
        return JsonResultImpl.getInstance(invalidDataService.quickFixAll());
    }

    /**
     * 批量丢弃
     */
    @PostMapping("discard")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> discard(@RequestBody List<Long> ids) {
        return JsonResultImpl.getInstance(invalidDataService.discard(ids));
    }

    /**
     * 丢弃所有可丢弃数据
     */
    @PostMapping("discard/all")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> discardAll() {
        return JsonResultImpl.getInstance(invalidDataService.discardAll());
    }

    /**
     * 按条件批量丢弃
     */
    @PostMapping("discard/byQuery")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> discardByQuery(@RequestBody InvalidDataQuery query) {
        return JsonResultImpl.getInstance(invalidDataService.discardByQuery(query));
    }

    /**
     * 按条件批量发布为可认领
     */
    @PostMapping("publish/byQuery")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> publishByQuery(@RequestBody InvalidDataQuery query) {
        return JsonResultImpl.getInstance(invalidDataService.publishByQuery(query));
    }

    /**
     * 按条件批量取消发布
     */
    @PostMapping("unpublish/byQuery")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> unpublishByQuery(@RequestBody InvalidDataQuery query) {
        return JsonResultImpl.getInstance(invalidDataService.unpublishByQuery(query));
    }

    /**
     * 标记处理完成（UNIQUE模式）
     */
    @PostMapping("markCompleted/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> markCompleted(@PathVariable Long id) {
        invalidDataService.markCompleted(id);
        return JsonResult.emptySuccess();
    }

    /**
     * 一键清理所有状态为已完成的失效数据
     */
    @PostMapping("cleanCompleted")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> cleanCompleted() {
        return JsonResultImpl.getInstance(invalidDataService.cleanCompleted());
    }

    /**
     * 一键将所有已认领的失效数据标记为已完成
     */
    @PostMapping("markClaimedCompleted")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> markClaimedCompleted() {
        return JsonResultImpl.getInstance(invalidDataService.markAllClaimedAsCompleted());
    }

    /**
     * 批量认领预览。
     * <p>查询匹配条件的可认领失效数据（UNIQUE 模式 + 失效物理存储），
     * 解析每条记录认领后的保存路径与文件名，最多返回前 10 条预览结果。</p>
     */
    @PostMapping("batchClaim/preview")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<List<ClaimPreviewItem>> batchClaimPreview(@RequestBody @Valid BatchClaimParam param) {
        return JsonResultImpl.getInstance(claimService.previewBatchClaim(param));
    }

    /**
     * 批量认领失效数据（仅限管理员调用）。
     * <p>查询匹配条件的可认领失效数据（UNIQUE 模式 + 失效物理存储），
     * 逐条解析保存路径与文件名后执行认领，跳过失败项继续处理后续记录。</p>
     */
    @PostMapping("batchClaim")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> batchClaim(@RequestBody @Valid BatchClaimParam param) {
        return JsonResultImpl.getInstance(claimService.batchClaim(param));
    }

    /**
     * 批量撤回认领（仅限管理员调用）。
     * <p>根据查询条件筛选已认领的失效数据（状态强制为 CLAIMED），逐条删除对应的文件记录、
     * 将认领记录标记为已撤回，并将失效数据状态恢复为待处理。支持 Groovy 脚本过滤。</p>
     */
    @PostMapping("batchRevokeClaim/byQuery")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<BatchResult> revokeClaimByQuery(@RequestBody InvalidDataQuery query) {
        return JsonResultImpl.getInstance(claimService.batchRevoke(query));
    }

    // === 普通用户查询 ===

    /**
     * 查询已发布可认领的失效数据列表（分页+筛选，状态固定为已发布）
     */
    @GetMapping("publishedList")
    public JsonResult<CommonPageInfo<InvalidDataRecord>> publishedList(InvalidDataQuery query, PageableRequest pageableRequest) {
        return JsonResultImpl.getInstance(invalidDataService.listPublished(query, pageableRequest));
    }

    // === 认领操作（普通用户+管理员） ===

    /**
     * 认领失效数据（UNIQUE模式）
     */
    @PostMapping("claim")
    public JsonResult<?> claim(@RequestBody @Valid ClaimParam param) throws IOException {
        claimService.claim(param);
        return JsonResult.emptySuccess();
    }

    /**
     * 查看某条失效数据的认领记录
     */
    @GetMapping("claims/{invalidDataId}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<List<ClaimRecord>> claims(@PathVariable Long invalidDataId) {
        return JsonResultImpl.getInstance(claimService.listByInvalidDataId(invalidDataId));
    }

    /**
     * 查看我的认领记录
     */
    @GetMapping("myClaims")
    public JsonResult<CommonPageInfo<ClaimRecord>> myClaims(@UID Long uid, Pageable pageable) {
        return JsonResultImpl.getInstance(claimService.listByUid(uid, pageable));
    }
}
