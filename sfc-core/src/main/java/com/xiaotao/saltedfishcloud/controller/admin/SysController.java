package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.model.SystemInfoVO;
import com.xiaotao.saltedfishcloud.model.TimestampRecord;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.vo.SystemOverviewVO;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import java.util.Collection;

@RestController
@RequestMapping(SysController.prefix)
@RolesAllowed({"ADMIN"})
@Validated
public class SysController {
    public static final String prefix = "/api/admin/sys/";
    @Resource
    private SysProperties sysProperties;
    @Resource
    private AdminService adminService;

    @GetMapping("restart")
    @ApiOperation("重启咸鱼云系统")
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

    @ApiOperation("获取当前系统信息")
    @GetMapping("getCurSystemInfo")
    public JsonResult<SystemInfoVO> getCurSystemInfo(@RequestParam(value = "nodeId", required = false) Long nodeId) {
        return JsonResultImpl.getInstance(adminService.getCurSystemInfo(nodeId, true));
    }

    @ApiOperation("列出系统一段时间范围内的信息采集集合")
    @GetMapping("listSystemInfo")
    public JsonResult<Collection<TimestampRecord<SystemInfoVO>>> listSystemInfo(@RequestParam(value = "nodeId", required = false) Long nodeId) {
        return JsonResultImpl.getInstance(adminService.listSystemInfo(nodeId));
    }

}
