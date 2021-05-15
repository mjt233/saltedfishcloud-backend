package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.BlockWhileSwitching;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import java.io.IOException;

@RestController
@RequestMapping(AdminController.prefix)
@RolesAllowed({"ADMIN"})
public class AdminController {
    public static final String prefix = "/api/admin";
    @Resource
    private ConfigService configService;

    @PutMapping("store/type")
    @BlockWhileSwitching
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
}
