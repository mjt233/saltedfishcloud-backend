package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.json.JsonResult;
import com.xiaotao.saltedfishcloud.entity.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.service.file.FileRecordSyncService;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import lombok.var;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping(StoreController.prefix)
public class StoreController {
    public static final String prefix = "/api/admin/store/";

    @Resource
    private AdminService adminService;
    @Resource
    private FileRecordSyncService syncService;
    @Resource
    private UserDao userDao;
    @Resource
    private SysProperties sysProperties;

    /**
     * 获取存储状态
     */
    @GetMapping("state")
    public JsonResult getStoreState() {
        return JsonResultImpl.getInstance(adminService.getStoreState());
    }

    /**
     * 立即执行同步
     * @TODO 支持精准同步参数控制
     */
    @PostMapping("sync")
    public JsonResult sync(@RequestParam(name = "all", defaultValue = "false") Boolean all) throws Exception {
        if (sysProperties.getStore().getMode() == StoreMode.UNIQUE) {
            return JsonResultImpl.getInstance(400, null, "UNIQUE模式不需要同步");
        }
        if (all) {
            var users = userDao.getUserList();
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
