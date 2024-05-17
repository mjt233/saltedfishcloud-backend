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
@RequestMapping("/api/proxy")
@Validated
public class ProxyController {
    @Autowired
    private ProxyInfoService proxyInfoService;

    /**
     * 保存代理信息（可新增、编辑）
     * @param proxyInfo 代理信息
     */
    @PostMapping("save")
    public JsonResult<ProxyInfo> save(@RequestBody ProxyInfo proxyInfo) {
        proxyInfoService.saveWithOwnerPermissions(proxyInfo);
        return JsonResultImpl.getInstance(proxyInfo);
    }

    /**
     * 按用户id获取代理节点
     * @param uid               用户id
     * @param pageableRequest   分页参数，可为null
     */
    @GetMapping("findByUid")
    public JsonResult<CommonPageInfo<ProxyInfo>> findByUid(Long uid, @RequestParam(required = false) PageableRequest pageableRequest) {
        return JsonResultImpl.getInstance(proxyInfoService.findByUidWithOwnerPermissions(uid, pageableRequest));
    }

    /**
     * （管理员）获取所有代理节点
     */
    @GetMapping
    @RolesAllowed({"ADMIN"})
    public JsonResult<List<ProxyInfo>> getAllProxy() {
        return JsonResultImpl.getInstance(proxyInfoService.findAll());
    }

    /**
     * 删除代理
     * @param proxyId   代理id
     */
    @DeleteMapping
    public JsonResult<?> deleteProxy(@RequestParam Long proxyId) {
        proxyInfoService.deleteWithOwnerPermissions(proxyId);
        return JsonResult.emptySuccess();
    }

    /**
     * 测试代理连通性
     * @param proxyId   代理id
     * @param timeout   超时（ms）
     * @param useCache  是否使用缓存结果
     * @return          是否连通，连同为true，否则为false
     */
    @GetMapping("test")
    public JsonResult<Boolean> test(@RequestParam("proxyId") Long proxyId,
                                    @RequestParam(value = "timeout", defaultValue = "10000") int timeout,
                                    @RequestParam(value = "useCache",defaultValue = "true") boolean useCache
    ) {
        return JsonResultImpl.getInstance(proxyInfoService.testProxy(proxyId, timeout, useCache));
    }
}
