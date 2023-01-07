package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.DesktopComponentConfig;
import com.xiaotao.saltedfishcloud.service.desktop.DesktopComponentConfigService;
import com.xiaotao.saltedfishcloud.service.desktop.DesktopComponentService;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/desktop")
public class DesktopController {
    @Autowired
    private DesktopComponentService desktopComponentService;

    @Autowired
    private DesktopComponentConfigService desktopComponentConfigService;

    /**
     * 列出所有可配置的组件
     */
    @GetMapping("listAllComponent")
    public JsonResult listAllComponent() {
        return JsonResultImpl.getInstance(desktopComponentService.listAllComponents());
    }

    /**
     * 删除一个组件配置
     */
    @PostMapping("deleteConfig")
    public JsonResult deleteConfig(@RequestParam("id") Long id) {
        desktopComponentConfigService.remove(id);
        return JsonResult.emptySuccess();
    }

    /**
     * 获取用户配置的组件
     * @param uid   用户id
     */
    @GetMapping("listComponentConfig")
    @AllowAnonymous
    public JsonResult listComponentConfig(@UID @RequestParam("uid") Long uid) {
        return JsonResultImpl.getInstance(desktopComponentConfigService.listByUid(uid));
    }

    /**
     * 保存一个桌面组件配置
     * @param config    配置信息
     */
    @PostMapping("saveComponentConfig")
    public JsonResult saveComponentConfig(@RequestBody DesktopComponentConfig config) {
        desktopComponentConfigService.save(config);
        return JsonResult.emptySuccess();
    }
}
