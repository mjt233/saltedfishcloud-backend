package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import java.util.LinkedHashMap;

@RestController
@RequestMapping(SysController.prefix)
@RolesAllowed({"ADMIN"})
@Validated
public class SysController {
    public static final String prefix = "/api/admin/sys/";
    @Resource
    private SysProperties sysProperties;
    @Resource
    private AdminService adminService;


    @GetMapping("overview")
    public JsonResult getOverview() {
        LinkedHashMap<String, Object> res = JsonResultImpl.getDataMap();
        res.put("store", adminService.getStoreState());
        res.put("invite_reg_code", sysProperties.getCommon().getRegCode());
        return JsonResultImpl.getInstance(res);
    }


}
