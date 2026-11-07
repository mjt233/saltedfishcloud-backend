package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.DesktopComponent;
import com.sfc.common.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.model.po.DesktopComponentConfig;
import com.xiaotao.saltedfishcloud.service.desktop.DesktopComponentConfigService;
import com.xiaotao.saltedfishcloud.service.desktop.DesktopComponentService;
import com.sfc.cloud.data.validation.SfcUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public SfcJsonResult<List<DesktopComponent>> listAllComponent() {
        return SfcJsonResult.getInstance(desktopComponentService.listAllComponents());
    }

    /**
     * 删除一个组件配置
     */
    @PostMapping("deleteConfig")
    public SfcJsonResult<Object> deleteConfig(@RequestParam("id") Long id) {
        desktopComponentConfigService.remove(id);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 获取用户配置的组件
     * @param uid   用户id
     */
    @GetMapping("listComponentConfig")
    @AllowAnonymous
    public SfcJsonResult<List<DesktopComponentConfig>> listComponentConfig(@SfcUID @RequestParam("uid") Long uid) {
        return SfcJsonResult.getInstance(desktopComponentConfigService.listByUid(uid));
    }

    /**
     * 保存一个桌面组件配置
     * @param config    配置信息
     */
    @PostMapping("saveComponentConfig")
    public SfcJsonResult<Object> saveComponentConfig(@RequestBody DesktopComponentConfig config) {
        desktopComponentConfigService.save(config);
        return SfcJsonResult.emptySuccess();
    }
}