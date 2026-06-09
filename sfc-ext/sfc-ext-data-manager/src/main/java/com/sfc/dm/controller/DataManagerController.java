package com.sfc.dm.controller;

import com.sfc.dm.service.DataExportService;
import com.sfc.dm.service.DataImportService;
import com.sfc.dm.service.FileMetadataBackupService;
import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import jakarta.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 数据管理控制器
 * <p>提供网盘数据的导出、导入及文件元数据备份管理接口</p>
 */
@RestController
@RequestMapping(DataManagerController.PREFIX)
@RolesAllowed(SysRole.ADMIN)
@RequiredArgsConstructor
public class DataManagerController {
    public static final String PREFIX = "/api/dataManager";

    private final DataExportService dataExportService;
    private final DataImportService dataImportService;
    private final FileMetadataBackupService fileMetadataBackupService;

    /**
     * 导出网盘数据
     *
     * @param uid 用户ID
     * @return 导出结果
     */
    @PostMapping("export")
    public JsonResult<?> exportData(@UID @RequestParam Long uid) {
        // TODO 实现数据导出逻辑
        return JsonResult.emptySuccess();
    }

    /**
     * 导入网盘数据
     *
     * @param uid 用户ID
     * @return 导入结果
     */
    @PostMapping("import")
    public JsonResult<?> importData(@UID @RequestParam Long uid) {
        // TODO 实现数据导入逻辑
        return JsonResult.emptySuccess();
    }

    /**
     * 触发文件元数据备份
     *
     * @return 备份结果
     */
    @PostMapping("backup/metadata")
    public JsonResult<?> triggerMetadataBackup() {
        // TODO 实现文件元数据备份触发逻辑
        return JsonResult.emptySuccess();
    }
}
