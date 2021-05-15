package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.RolesAllowed;

@RequestMapping(DebugController.prefix)
@RolesAllowed({"ADMIN"})
@RestController
public class DebugController {
    public static final String prefix = "/api/admin/debug/";

    @PutMapping("switching")
    public JsonResult setSwitchingState(@RequestParam boolean state) {
        DiskConfig.setStoreSwitching(state);
        return JsonResult.getInstance();
    }

    @GetMapping("switching")
    public JsonResult getSwitchingState() {
        return JsonResult.getInstance(DiskConfig.isStoreSwitching());
    }
}
