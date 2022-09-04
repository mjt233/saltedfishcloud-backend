package com.xiaotao.saltedfishcloud.controller.admin;


import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;

@RestController
@RequestMapping(ProxyController.PREFIX)
@RolesAllowed({"ADMIN"})
@Validated
public class ProxyController {
    public static final String PREFIX = "/api/admin/sys/proxy";

    @Resource
    private ProxyDao proxyDao;


    @PostMapping
    public JsonResult addProxy(@Validated ProxyInfo info) {
        try {
            proxyDao.addProxy(info);
        } catch (DuplicateKeyException e) {
            throw new JsonException(400, "名称已存在");
        }
        return JsonResult.emptySuccess();
    }

    @GetMapping
    public JsonResult getAllProxy() {
        return JsonResultImpl.getInstance(proxyDao.getAllProxy());
    }

    @PutMapping
    public JsonResult modifyProxy(@Valid ProxyInfo info, String proxyName) {
        try {
            if (proxyDao.modifyProxy(proxyName, info) == 0) {
                throw new JsonException(400, "代理" + proxyName + "不存在");
            }
        } catch (DuplicateKeyException e) {
            throw new JsonException(400, "新名称已存在");
        }
        return JsonResult.emptySuccess();
    }

    @DeleteMapping
    public JsonResult deleteProxy(@RequestParam String proxyName) {
        if (proxyDao.removeProxy(proxyName) == 0) {
            throw new JsonException(400, "代理" + proxyName + "不存在");
        }
        return JsonResult.emptySuccess();
    }
}
