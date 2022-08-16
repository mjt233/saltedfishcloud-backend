package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.ext.PluginService;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.RolesAllowed;

@RestController
@RequestMapping("/api/plugin")
public class PluginController {
    @Autowired
    private PluginService pluginService;

    @GetMapping("/getAllPlugins")
    @RolesAllowed({"ADMIN"})
    public JsonResult getAllPlugins() {
        return JsonResultImpl.getInstance(pluginService.listPlugins());
    }
}
