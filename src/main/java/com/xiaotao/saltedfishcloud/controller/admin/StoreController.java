package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import com.xiaotao.saltedfishcloud.service.sync.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.io.IOException;

@RestController
@RequestMapping(StoreController.prefix)
public class StoreController {
    public static final String prefix = "/api/admin/store/";

    @Resource
    private AdminService adminService;
    @Resource
    private SyncService syncService;

    /**
     * 获取存储状态
     */
    @GetMapping("state")
    public JsonResult getStoreState() {
        return JsonResult.getInstance(adminService.getStoreState());
    }

    /**
     * 立即执行同步
     */
    @PostMapping("sync")
    public JsonResult sync() throws Exception {
        syncService.syncLocal(User.getPublicUser());
        return JsonResult.getInstance();
    }
}
