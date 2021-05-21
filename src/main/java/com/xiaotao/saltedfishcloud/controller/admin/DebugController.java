package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.RolesAllowed;
import java.util.LinkedHashMap;

@RequestMapping(DebugController.prefix)
@RolesAllowed({"ADMIN"})
@RestController
public class DebugController {
    public static final String prefix = "/api/admin/debug/";

    @PutMapping("readOnly")
    public JsonResult setReadOnlyLevel(@RequestParam String level) {
        ReadOnlyLevel r;
        try {
            r = ReadOnlyLevel.valueOf(level);
        } catch (Exception e) {
            r = null;
        }
        DiskConfig.setReadOnlyLevel(r);
        return JsonResult.getInstance();
    }

    @GetMapping("readOnly")
    public JsonResult getReadOnlyLevel() {
        return JsonResult.getInstance(DiskConfig.getReadOnlyLevel());
    }

    @GetMapping("options")
    public JsonResult getAllOptions() {
        LinkedHashMap<String, Object> data = JsonResult.getDataMap();
        data.put("read_only_level", DiskConfig.getReadOnlyLevel());
        data.put("sync_delay", DiskConfig.SYNC_DELAY);
        return JsonResult.getInstance(data);
    }
}
