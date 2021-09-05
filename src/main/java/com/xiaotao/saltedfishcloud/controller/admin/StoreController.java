package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import com.xiaotao.saltedfishcloud.service.sync.SyncService;
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
    private SyncService syncService;
    @Resource
    private UserDao userDao;

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
    public JsonResult sync(@RequestParam(name = "all", defaultValue = "false") Boolean all) throws Exception {
        if (all) {
            var users = userDao.getUserList();
            users.add(User.getPublicUser());
            for (User user : users) {
                syncService.syncLocal(user);
            }
        } else {
            syncService.syncLocal(User.getPublicUser());
        }
        return JsonResult.getInstance();
    }
}
