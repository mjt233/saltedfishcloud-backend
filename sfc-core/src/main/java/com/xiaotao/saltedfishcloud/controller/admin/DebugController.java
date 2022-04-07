package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.entity.json.JsonResult;
import com.xiaotao.saltedfishcloud.entity.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import lombok.var;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import java.util.LinkedHashMap;

@RequestMapping(DebugController.prefix)
@RolesAllowed({"ADMIN"})
@RestController
public class DebugController {
    public static final String prefix = "/api/admin/debug/";
    @Resource
    private ConfigDao configDao;
    @Resource
    private SysProperties sysProperties;

    /**
     * @TODO 修改路由readOnly为protectMode
     */
    @PutMapping("readOnly")
    public JsonResult setReadOnlyLevel(@RequestParam String level) {
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
    public JsonResult getReadOnlyLevel() {
        return JsonResultImpl.getInstance(SysRuntimeConfig.getInstance().getProtectModeLevel());
    }

    @GetMapping("options")
    public JsonResult getAllOptions() {
        LinkedHashMap<String, Object> data = JsonResultImpl.getDataMap();
        var conf = configDao.getAllConfig();
        if (conf != null) {
            conf.forEach(e -> {
                data.put(e.getKey(), e.getValue());
            });
        }
        data.put("READ_ONLY_LEVEL", SysRuntimeConfig.getInstance().getProtectModeLevel());
        data.put("read_only_level", SysRuntimeConfig.getInstance().getProtectModeLevel());
        data.put("sync_delay", sysProperties.getSync().getInterval());

        return JsonResultImpl.getInstance(200, data, "小写字段将在后续版本中废弃");
    }
}
