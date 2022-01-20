package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.ConfigDao;
import com.xiaotao.saltedfishcloud.entity.json.JsonResult;
import com.xiaotao.saltedfishcloud.entity.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
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

    @PutMapping("readOnly")
    public JsonResult setReadOnlyLevel(@RequestParam String level) {
        ReadOnlyLevel r;
        try {
            r = ReadOnlyLevel.valueOf(level);
        } catch (Exception e) {
            r = null;
        }
        DiskConfig.setReadOnlyLevel(r);
        return JsonResult.emptySuccess();
    }

    @GetMapping("readOnly")
    public JsonResult getReadOnlyLevel() {
        return JsonResultImpl.getInstance(DiskConfig.getReadOnlyLevel());
    }

    @GetMapping("options")
    public JsonResult getAllOptions() {
        LinkedHashMap<String, Object> data = JsonResultImpl.getDataMap();
        var conf = configDao.getAllConfig();
        if (conf != null) {
            conf.forEach(e -> {
                data.put(e.getKey().toString(), e.getValue());
            });
        }
        data.put("READ_ONLY_LEVEL", DiskConfig.getReadOnlyLevel());
        data.put("read_only_level", DiskConfig.getReadOnlyLevel());
        data.put("sync_delay", DiskConfig.SYNC_DELAY);

        return JsonResultImpl.getInstance(200, data, "小写字段将在后续版本中废弃");
    }
}
