package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.model.SystemInfoVO;
import com.xiaotao.saltedfishcloud.model.TimestampRecord;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping(SysController.prefix)
@RolesAllowed({"ADMIN"})
@Validated
public class SysController {
    public static final String prefix = "/api/admin/sys/";
    @Resource
    private AdminService adminService;
    @Resource
    private ThumbnailService thumbnailService;

    @GetMapping("restart")
    @Operation(summary = "重启咸鱼云系统")
    public JsonResult<Object> restart(@RequestParam(value = "withCluster", defaultValue = "true", required = false) Boolean withCluster) {
        adminService.restart(withCluster);
        return JsonResult.emptySuccess();
    }

    /**
     * 获取系统总览参数
     */
    @GetMapping("overview")
    public JsonResult<SystemOverviewVO> getOverview(@RequestParam(value = "nodeId", required = false) Long nodeId) {
        return JsonResultImpl.getInstance(adminService.getOverviewData(nodeId));
    }

    @Operation(summary = "获取当前系统信息")
    @GetMapping("getCurSystemInfo")
    public JsonResult<SystemInfoVO> getCurSystemInfo(@RequestParam(value = "nodeId", required = false) Long nodeId) {
        return JsonResultImpl.getInstance(adminService.getCurSystemInfo(nodeId, true));
    }

    @Operation(summary = "列出系统一段时间范围内的信息采集集合")
    @GetMapping("listSystemInfo")
    public JsonResult<Collection<TimestampRecord<SystemInfoVO>>> listSystemInfo(@RequestParam(value = "nodeId", required = false) Long nodeId) {
        return JsonResultImpl.getInstance(adminService.listSystemInfo(nodeId));
    }

    @Operation(summary = "获取系统支持的缩略图生成器名称")
    @GetMapping("getThumbnailHandlerNames")
    public JsonResult<List<String>> getThumbnailHandlerNames() {
        return JsonResultImpl.getInstance(thumbnailService.getRegisteredHandler().stream().map(ThumbnailHandler::getName).distinct().toList());
    }
}
