package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.jpa.ConfigRepo;
import com.xiaotao.saltedfishcloud.model.po.Config;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import java.util.LinkedHashMap;
import java.util.List;

@RequestMapping(DebugController.prefix)
@RolesAllowed({"ADMIN"})
@RestController
public class DebugController {
    public static final String prefix = "/api/admin/debug/";
    @Resource
    private ConfigRepo configDao;
    @Resource
    private SysCommonConfig sysCommonConfig;

    @PutMapping("readOnly")
    public JsonResult<Object> setReadOnlyLevel(@RequestParam String level) {
        ProtectLevel r;
        try {
            r = ProtectLevel.valueOf(level);
        } catch (Exception e) {
            r = ProtectLevel.OFF;
        }
        SysRuntimeConfig.getInstance().setProtectModeLevel(r);
        return JsonResult.emptySuccess();
    }

    @GetMapping("readOnly")
    public JsonResult<ProtectLevel> getReadOnlyLevel() {
        return JsonResultImpl.getInstance(SysRuntimeConfig.getInstance().getProtectModeLevel());
    }

    @GetMapping("options")
    public JsonResult<LinkedHashMap<String, Object>> getAllOptions() {
        LinkedHashMap<String, Object> data = JsonResultImpl.getDataMap();
        List<Config> conf = configDao.getAllConfig();
        if (conf != null) {
            conf.forEach(e -> data.put(e.getItemKey(), e.getItemValue()));
        }
        data.put("READ_ONLY_LEVEL", SysRuntimeConfig.getInstance().getProtectModeLevel());
        data.put("read_only_level", SysRuntimeConfig.getInstance().getProtectModeLevel());
        return JsonResultImpl.getInstance(200, data, "小写字段将在后续版本中废弃");
    }
}