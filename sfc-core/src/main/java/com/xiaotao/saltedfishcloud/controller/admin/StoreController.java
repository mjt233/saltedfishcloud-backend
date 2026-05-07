package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.dao.jpa.UserRepo;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileRecordSyncService;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(StoreController.prefix)
public class StoreController {
    public static final String prefix = "/api/admin/store/";

    @Resource
    private AdminService adminService;
    @Resource
    private FileRecordSyncService syncService;
    @Resource
    private UserRepo userRepo;
    @Resource
    private SysCommonConfig sysCommonConfig;

//    /**
//     * 获取存储状态
//     */
//    @GetMapping("state")
//    public JsonResult getStoreState() {
//        return JsonResultImpl.getInstance(adminService.getStoreState());
//    }

    /**
     * 立即执行同步
     * todo 支持精准同步参数控制
     */
    @PostMapping("sync")
    public JsonResult<Object> sync(@RequestParam(name = "all", defaultValue = "false") Boolean all) throws Exception {
        if (sysCommonConfig.getStoreMode() == StoreMode.UNIQUE) {
            return JsonResultImpl.getInstance(400, null, "UNIQUE模式不需要同步");
        }
        if (all) {
            List<User> users = userRepo.getUserList();
            users.add(User.getPublicUser());
            for (User user : users) {
                try {
                    syncService.doSync(user.getId(), false);
                } catch (Exception e) { e.printStackTrace(); }
            }
        } else {
            syncService.doSync(User.getPublicUser().getId(), false);
        }
        return JsonResult.emptySuccess();
    }
}
