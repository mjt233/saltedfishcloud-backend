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
import com.sfc.cloud.constant.SfcSysRole;
import com.sfc.cloud.model.SfcPageInfo;
import com.sfc.cloud.model.json.SfcJsonResult;
import com.sfc.cloud.model.param.SfcPageableRequest;
import com.sfc.cloud.utils.SfcMapperHolder;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import com.sfc.cloud.data.validation.SfcUID;
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
    public SfcJsonResult<List<FileTypeProviderInfo>> listProviders() {
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
        return SfcJsonResult.getInstance(list);
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
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<Long> detect() throws IOException {
        checkConcurrentTask();
        AsyncTaskCreateParam param = new AsyncTaskCreateParam();
        param.setName("失效数据检测");
        param.setTaskType(DataManagerTaskType.INVALID_DATA_DETECT);
        param.setCpuOverhead(50);
        AsyncTaskRecord record = asyncTaskManager.createTask(param);
        return SfcJsonResult.getInstance(record.getId());
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
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<Long> identify(@RequestBody(required = false) IdentifyParam param) throws IOException {
        checkConcurrentTask();
        AsyncTaskCreateParam taskParam = new AsyncTaskCreateParam();
        taskParam.setName("文件类型识别");
        taskParam.setTaskType(DataManagerTaskType.FILE_TYPE_CHECK);
        taskParam.setCpuOverhead(50);
        if (param != null && (param.getIds() != null || param.getReIdentify() != null)) {
            taskParam.setParams(SfcMapperHolder.toJson(param));
        }
        AsyncTaskRecord record = asyncTaskManager.createTask(taskParam);
        return SfcJsonResult.getInstance(record.getId());
    }

    /**
     * 查询失效数据列表（分页+筛选）
     *
     * @param filterId 可选，脚本筛选缓存ID。传此值时使用缓存中的筛选结果分页，忽略 query 中的 filterScript 字段。
     */
    @GetMapping("list")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<SfcPageInfo<InvalidDataRecord>> list(
            InvalidDataQuery query,
            @RequestParam(required = false) String filterId,
            SfcPageableRequest pageableRequest) {
        if (filterId != null && !filterId.isBlank()) {
            return SfcJsonResult.getInstance(invalidDataService.listByFilterId(filterId, pageableRequest));
        }
        return SfcJsonResult.getInstance(invalidDataService.list(query, pageableRequest));
    }

    /**
     * 提交脚本筛选，执行全量查询 + Groovy 脚本筛选，缓存结果 ID 列表并返回 filterId。
     * <p>筛选器将执行全表扫描（配合 DB 筛选条件），通过 Groovy 脚本对每条记录求值，<br>
     * 脚本末行表达式为 true 时保留该记录。筛选后的 ID 列表会缓存 {@code filterCacheTtl} 分钟，<br>
     * 随后可通过 {@code GET /list?filterId=xxx&page=0&size=20} 进行分页查询。</p>
     */
    @PostMapping("filter")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<InvalidDataFilterResult> filter(@RequestBody InvalidDataQuery query) {
        return SfcJsonResult.getInstance(invalidDataService.createFilter(query));
    }

    /**
     * 获取失效数据详情
     */
    @GetMapping("detail/{id}")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<InvalidDataRecord> detail(@PathVariable Long id) {
        return SfcJsonResult.getInstance(invalidDataService.getDetail(id));
    }

    /**
     * 批量发布为可认领
     */
    @PostMapping("publish")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> publish(@RequestBody List<Long> idList) {
        return SfcJsonResult.getInstance(invalidDataService.publish(idList));
    }

    /**
     * 批量取消发布
     */
    @PostMapping("unpublish")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> unpublish(@RequestBody List<Long> idList) {
        return SfcJsonResult.getInstance(invalidDataService.unpublish(idList));
    }

    /**
     * 批量快速修复
     */
    @PostMapping("quickFix")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> quickFix(@RequestBody List<Long> ids) {
        return SfcJsonResult.getInstance(invalidDataService.quickFix(ids));
    }

    /**
     * 修复所有待处理数据
     */
    @PostMapping("quickFix/all")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> quickFixAll() {
        return SfcJsonResult.getInstance(invalidDataService.quickFixAll());
    }

    /**
     * 批量丢弃
     */
    @PostMapping("discard")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> discard(@RequestBody List<Long> ids) {
        return SfcJsonResult.getInstance(invalidDataService.discard(ids));
    }

    /**
     * 丢弃所有可丢弃数据
     */
    @PostMapping("discard/all")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> discardAll() {
        return SfcJsonResult.getInstance(invalidDataService.discardAll());
    }

    /**
     * 按条件批量丢弃
     */
    @PostMapping("discard/byQuery")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> discardByQuery(@RequestBody InvalidDataQuery query) {
        return SfcJsonResult.getInstance(invalidDataService.discardByQuery(query));
    }

    /**
     * 按条件批量发布为可认领
     */
    @PostMapping("publish/byQuery")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> publishByQuery(@RequestBody InvalidDataQuery query) {
        return SfcJsonResult.getInstance(invalidDataService.publishByQuery(query));
    }

    /**
     * 按条件批量取消发布
     */
    @PostMapping("unpublish/byQuery")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> unpublishByQuery(@RequestBody InvalidDataQuery query) {
        return SfcJsonResult.getInstance(invalidDataService.unpublishByQuery(query));
    }

    /**
     * 标记处理完成（UNIQUE模式）
     */
    @PostMapping("markCompleted/{id}")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<?> markCompleted(@PathVariable Long id) {
        invalidDataService.markCompleted(id);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 一键清理所有状态为已完成的失效数据
     */
    @PostMapping("cleanCompleted")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> cleanCompleted() {
        return SfcJsonResult.getInstance(invalidDataService.cleanCompleted());
    }

    /**
     * 一键将所有已认领的失效数据标记为已完成
     */
    @PostMapping("markClaimedCompleted")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> markClaimedCompleted() {
        return SfcJsonResult.getInstance(invalidDataService.markAllClaimedAsCompleted());
    }

    /**
     * 批量认领预览。
     * <p>查询匹配条件的可认领失效数据（UNIQUE 模式 + 失效物理存储），
     * 解析每条记录认领后的保存路径与文件名，最多返回前 10 条预览结果。</p>
     */
    @PostMapping("batchClaim/preview")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<List<ClaimPreviewItem>> batchClaimPreview(@RequestBody @Valid BatchClaimParam param) {
        return SfcJsonResult.getInstance(claimService.previewBatchClaim(param));
    }

    /**
     * 批量认领失效数据（仅限管理员调用）。
     * <p>查询匹配条件的可认领失效数据（UNIQUE 模式 + 失效物理存储），
     * 逐条解析保存路径与文件名后执行认领，跳过失败项继续处理后续记录。</p>
     */
    @PostMapping("batchClaim")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> batchClaim(@RequestBody @Valid BatchClaimParam param) {
        return SfcJsonResult.getInstance(claimService.batchClaim(param));
    }

    /**
     * 批量撤回认领（仅限管理员调用）。
     * <p>根据查询条件筛选已认领的失效数据（状态强制为 CLAIMED），逐条删除对应的文件记录、
     * 将认领记录标记为已撤回，并将失效数据状态恢复为待处理。支持 Groovy 脚本过滤。</p>
     */
    @PostMapping("batchRevokeClaim/byQuery")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<BatchResult> revokeClaimByQuery(@RequestBody InvalidDataQuery query) {
        return SfcJsonResult.getInstance(claimService.batchRevoke(query));
    }

    // === 普通用户查询 ===

    /**
     * 查询已发布可认领的失效数据列表（分页+筛选，状态固定为已发布）
     */
    @GetMapping("publishedList")
    public SfcJsonResult<SfcPageInfo<InvalidDataRecord>> publishedList(InvalidDataQuery query, SfcPageableRequest pageableRequest) {
        return SfcJsonResult.getInstance(invalidDataService.listPublished(query, pageableRequest));
    }

    // === 认领操作（普通用户+管理员） ===

    /**
     * 认领失效数据（UNIQUE模式）
     */
    @PostMapping("claim")
    public SfcJsonResult<?> claim(@RequestBody @Valid ClaimParam param) throws IOException {
        claimService.claim(param);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 查看某条失效数据的认领记录
     */
    @GetMapping("claims/{invalidDataId}")
    @RolesAllowed(SfcSysRole.ADMIN)
    public SfcJsonResult<List<ClaimRecord>> claims(@PathVariable Long invalidDataId) {
        return SfcJsonResult.getInstance(claimService.listByInvalidDataId(invalidDataId));
    }

    /**
     * 查看我的认领记录
     */
    @GetMapping("myClaims")
    public SfcJsonResult<SfcPageInfo<ClaimRecord>> myClaims(@SfcUID Long uid, Pageable pageable) {
        return SfcJsonResult.getInstance(claimService.listByUid(uid, pageable));
    }
}
