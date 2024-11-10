package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.model.PluginConfigNodeInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(ConfigController.PREFIX)
@RolesAllowed({"ADMIN"})
@Validated
public class ConfigController {
    public static final String PREFIX = "/api/admin/sys/config";

    @Resource
    private ConfigService configService;

    @GetMapping("config/{key}")
    public JsonResult<String> getConfig(@PathVariable String key) {
        String res = configService.getConfig(key);
        return JsonResultImpl.getInstance(res);
    }

    @PutMapping("/batchSetConfig")
    public JsonResult<?> batchSetConfig(@RequestBody List<NameValueType<String>> config) throws IOException {
        configService.batchSetConfig(config);
        return JsonResult.emptySuccess();
    }


    /**
     * 设置配置项参数值
     * @param key       配置项名
     * @param value     值
     */
    @PutMapping("config/{key}/{value}")
    public JsonResult<?> setConfig(@PathVariable String key, @PathVariable String  value) throws IOException {
        configService.setConfig(key, value);
        return JsonResult.emptySuccess();
    }

    @GetMapping("listPluginConfig")
    public JsonResult<List<PluginConfigNodeInfo>> listAllPluginConfig() {
        return JsonResultImpl.getInstance(configService.listPluginConfig());
    }
}
