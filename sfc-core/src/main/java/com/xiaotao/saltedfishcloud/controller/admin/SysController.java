package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import java.io.IOException;
import java.util.LinkedHashMap;

@RestController
@RequestMapping(SysController.prefix)
@RolesAllowed({"ADMIN"})
@Validated
public class SysController {
    public static final String prefix = "/api/admin/sys/";
    @Resource
    private ConfigService configService;
    @Resource
    private SysProperties sysProperties;
    @Resource
    private AdminService adminService;
    @Resource
    private ProxyDao proxyDao;


    @GetMapping("overview")
    public JsonResult getOverview() {
        LinkedHashMap<String, Object> res = JsonResultImpl.getDataMap();
        res.put("store", adminService.getStoreState());
        res.put("invite_reg_code", sysProperties.getCommon().getRegCode());
        return JsonResultImpl.getInstance(res);
    }


    @GetMapping({"settings", "config"})
    public JsonResult getSysSettings() {
        return JsonResultImpl.getInstance(configService.getAllConfig());
    }

    @GetMapping("configKeys")
    public JsonResult getConfigKeys() {
        return JsonResultImpl.getInstance(ConfigName.values());
    }

    @GetMapping("config/{key}")
    public JsonResult getConfig(@PathVariable String key) {
        String res = configService.getConfig(ConfigName.valueOf(key));
        return JsonResultImpl.getInstance(res);
    }


    /**
     * 设置存储类型
     * @param type      存储类型
     */
    @PutMapping("config/STORE_TYPE/{type}")
    @ProtectBlock
    public JsonResult setStoreType(@PathVariable("type") String type) throws IOException {
        try {
            StoreMode storeMode = StoreMode.valueOf(type.toUpperCase());
            if (configService.setStoreType(storeMode)) {
                return JsonResult.emptySuccess();
            } else {
                return JsonResultImpl.getInstance(202, sysProperties.getStore().getMode().toString(), "请求被忽略，模式无变化");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的类型，可选RAW或UNIQUE");
        }
    }

    /**
     * 设置配置项参数值
     * @param key       配置项名
     * @param value     值
     */
    @PutMapping("config/{key}/{value}")
    public JsonResult setConfig(@PathVariable String key, @PathVariable String  value) throws IOException {
        configService.setConfig(key, value);
        return JsonResult.emptySuccess();
    }

    @PostMapping("proxy")
    public JsonResult addProxy(@Validated ProxyInfo info) {
        try {
            proxyDao.addProxy(info);
        } catch (DuplicateKeyException e) {
            throw new JsonException(400, "名称已存在");
        }
        return JsonResult.emptySuccess();
    }

    @GetMapping("proxy")
    public JsonResult getAllProxy() {
        return JsonResultImpl.getInstance(proxyDao.getAllProxy());
    }

    @PutMapping("proxy")
    public JsonResult modifyProxy(@Valid ProxyInfo info, String proxyName) {
        if (proxyDao.modifyProxy(proxyName, info) == 0) {
            throw new JsonException(400, "代理" + proxyName + "不存在");
        }
        return JsonResult.emptySuccess();
    }

    @DeleteMapping("proxy")
    public JsonResult deleteProxy(@RequestParam String proxyName) {
        if (proxyDao.removeProxy(proxyName) == 0) {
            throw new JsonException(400, "代理" + proxyName + "不存在");
        }
        return JsonResult.emptySuccess();
    }


}
