package com.sfc.dm.controller;

import com.sfc.dm.constant.DataManagerTaskType;
import com.sfc.dm.model.dto.ClaimParam;
import com.sfc.dm.model.dto.FileTypeProviderInfo;
import com.sfc.dm.model.dto.InvalidDataQuery;
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
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
     * 获取系统当前支持的文件类型识别器信息
     */
    @GetMapping("providers")
    public JsonResult<List<FileTypeProviderInfo>> listProviders() {
        List<FileTypeProviderInfo> list = fileTypeChecker.getProviders().stream()
                .map(p -> new FileTypeProviderInfo(
                        p.getId(),
                        p.getTypeId(),
                        p.getTypeName(),
                        p.getSupportedFileExtensions(),
                        p.getMetadataDefines()
                ))
                .toList();
        return JsonResultImpl.getInstance(list);
    }

    // === 管理员操作 ===

    /**
     * 发起失效数据检测（异步任务）
     */
    @PostMapping("detect")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> detect() throws IOException {
        checkConcurrentTask();
        AsyncTaskCreateParam param = new AsyncTaskCreateParam();
        param.setName("失效数据检测");
        param.setTaskType(DataManagerTaskType.INVALID_DATA_DETECT);
        param.setCpuOverhead(50);
        asyncTaskManager.createTask(param);
        return JsonResult.emptySuccess();
    }

    /**
     * 发起文件识别（异步任务）
     */
    @PostMapping("identify")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> identify() throws IOException {
        checkConcurrentTask();
        AsyncTaskCreateParam param = new AsyncTaskCreateParam();
        param.setName("文件类型识别");
        param.setTaskType(DataManagerTaskType.FILE_TYPE_CHECK);
        param.setCpuOverhead(50);
        asyncTaskManager.createTask(param);
        return JsonResult.emptySuccess();
    }

    /**
     * 查询失效数据列表（分页+筛选）
     */
    @GetMapping("list")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<CommonPageInfo<InvalidDataRecord>> list(InvalidDataQuery query, Pageable pageable) {
        return JsonResultImpl.getInstance(invalidDataService.list(query, pageable));
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
     * 发布为可认领（UNIQUE模式）
     */
    @PostMapping("publish/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> publish(@PathVariable Long id) {
        invalidDataService.publish(id);
        return JsonResult.emptySuccess();
    }

    /**
     * 取消发布（UNIQUE模式）
     */
    @PostMapping("unpublish/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> unpublish(@PathVariable Long id) {
        invalidDataService.unpublish(id);
        return JsonResult.emptySuccess();
    }

    /**
     * 批量快速修复
     */
    @PostMapping("quickFix")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<Map<String, Object>> quickFix(@RequestBody List<Long> ids) {
        return JsonResultImpl.getInstance(invalidDataService.quickFix(ids));
    }

    /**
     * 修复所有待处理数据
     */
    @PostMapping("quickFix/all")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<Map<String, Object>> quickFixAll() {
        return JsonResultImpl.getInstance(invalidDataService.quickFixAll());
    }

    /**
     * 批量丢弃
     */
    @PostMapping("discard")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<Map<String, Object>> discard(@RequestBody List<Long> ids) {
        return JsonResultImpl.getInstance(invalidDataService.discard(ids));
    }

    /**
     * 丢弃所有可丢弃数据
     */
    @PostMapping("discard/all")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<Map<String, Object>> discardAll() {
        return JsonResultImpl.getInstance(invalidDataService.discardAll());
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

    // === 认领操作（普通用户+管理员） ===

    /**
     * 认领失效数据（UNIQUE模式）
     */
    @PostMapping("claim")
    public JsonResult<?> claim(@RequestBody @Valid ClaimParam param, @UID Long uid) {
        claimService.claim(param, uid);
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
