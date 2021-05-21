package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.annotations.ReadOnlyBlock;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.FileAnalyseDao;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

@RestController
@RequestMapping(SysController.prefix)
@RolesAllowed({"ADMIN"})
public class SysController {
    public static final String prefix = "/api/admin/sys/";
    @Resource
    private ConfigService configService;
    @Resource
    private AdminService adminService;

    @PutMapping("store/type")
    @ReadOnlyBlock
    public JsonResult setStoreType(@RequestParam("type") String type) throws IOException {
        try {
            StoreType storeType = StoreType.valueOf(type.toUpperCase());
            if (configService.setStoreType(storeType)) {
                return JsonResult.getInstance();
            } else {
                return JsonResult.getInstance(0, DiskConfig.STORE_TYPE.toString(), "请求被忽略，模式无变化");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的类型，可选RAW或UNIQUE");
        }
    }

    @GetMapping("overview")
    public JsonResult getOverview() {
        LinkedHashMap<String, Object> res = JsonResult.getDataMap();
        res.put("store", adminService.getStoreState());
        res.put("invite_reg_code", DiskConfig.REG_CODE);
        return JsonResult.getInstance(res);
    }

    @GetMapping("store/state")
    public JsonResult getStoreState() {
        return JsonResult.getInstance(adminService.getStoreState());
    }

    @PutMapping("regCode/{code}")
    public JsonResult setInviteRegCode(@PathVariable("code") String code) {
        configService.setInviteRegCode(code);
        return JsonResult.getInstance();
    }

    @GetMapping("settings")
    public JsonResult getSysSettings() {
        LinkedHashMap<String, Object> data = JsonResult.getDataMap();
        data.put("invite_reg_code", DiskConfig.REG_CODE);
        return JsonResult.getInstance(data);
    }
}
