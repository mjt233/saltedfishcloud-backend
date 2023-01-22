package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;

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
    public JsonResult restart() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(500);
                SpringContextUtils.restart();
            } catch (InterruptedException ignore) {
            }
        });
        thread.start();
        return JsonResult.emptySuccess();
    }

    /**
     * 获取系统总览参数
     */
    @GetMapping("overview")
    public JsonResult getOverview() {
        return JsonResultImpl.getInstance(adminService.getOverviewData());
    }


}
