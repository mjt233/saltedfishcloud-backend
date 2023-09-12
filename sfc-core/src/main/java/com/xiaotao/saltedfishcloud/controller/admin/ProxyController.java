package com.xiaotao.saltedfishcloud.controller.admin;


import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.RolesAllowed;
import java.util.List;

@RestController
@RequestMapping(ProxyController.PREFIX)
@Validated
public class ProxyController {
    public static final String PREFIX = "/api/admin/sys/proxy";

    @Autowired
    private ProxyInfoService proxyInfoService;

    @PostMapping("save")
    public JsonResult<ProxyInfo> save(@RequestBody ProxyInfo proxyInfo) {
        proxyInfoService.saveWithOwnerPermissions(proxyInfo);
        return JsonResultImpl.getInstance(proxyInfo);
    }

    @GetMapping("findByUid")
    public JsonResult<CommonPageInfo<ProxyInfo>> findByUid(Long uid, @RequestParam(required = false) PageableRequest pageableRequest) {
        return JsonResultImpl.getInstance(proxyInfoService.findByUidWithOwnerPermissions(uid, pageableRequest));
    }

    @GetMapping
    @RolesAllowed({"ADMIN"})
    public JsonResult<List<ProxyInfo>> getAllProxy() {
        return JsonResultImpl.getInstance(proxyInfoService.findAll());
    }

    @DeleteMapping
    public JsonResult<?> deleteProxy(@RequestParam Long proxyId) {
        proxyInfoService.deleteWithOwnerPermissions(proxyId);
        return JsonResult.emptySuccess();
    }

    @GetMapping("test")
    public JsonResult<Boolean> test(@RequestParam("proxyId") Long proxyId,
                                    @RequestParam(value = "timeout", defaultValue = "10000") int timeout,
                                    @RequestParam(value = "useCache",defaultValue = "true") boolean useCache
    ) {
        return JsonResultImpl.getInstance(proxyInfoService.testProxy(proxyId, timeout, useCache));
    }
}
