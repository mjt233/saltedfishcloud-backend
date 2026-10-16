package com.xiaotao.saltedfishcloud.controller.admin;


import com.sfc.cloud.model.SfcPageInfo;
import com.sfc.cloud.constant.SfcUserConstants;
import com.sfc.cloud.model.json.SfcJsonResult;
import com.sfc.cloud.model.param.SfcPageableRequest;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

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
    public SfcJsonResult<ProxyInfo> save(@RequestBody ProxyInfo proxyInfo) {
        proxyInfoService.saveWithOwnerPermissions(proxyInfo);
        return SfcJsonResult.getInstance(proxyInfo);
    }

    /**
     * 按用户id获取代理节点
     * @param uid               用户id
     * @param pageableRequest   分页参数，可为null
     */
    @GetMapping("findByUid")
    public SfcJsonResult<SfcPageInfo<ProxyInfo>> findByUid(Long uid, @RequestParam(required = false) SfcPageableRequest pageableRequest) {
        SfcPageInfo<ProxyInfo> res = proxyInfoService.findByUidWithOwnerPermissions(uid, pageableRequest);

        // 非管理员获取公共代理列表时，移除所有非公开的代理
        if (Objects.equals(SfcUserConstants.PUBLIC_USER_ID, uid) && !SfcUIDValidator.validate(uid, true)) {
            res.setContent(res.getContent().stream().filter(proxy -> !Boolean.TRUE.equals(proxy.getIsProtect())).toList());
        }
        return SfcJsonResult.getInstance(res);
    }

    /**
     * （管理员）获取所有代理节点
     */
    @GetMapping
    @RolesAllowed({"ADMIN"})
    public SfcJsonResult<List<ProxyInfo>> getAllProxy() {
        return SfcJsonResult.getInstance(proxyInfoService.findAll());
    }

    /**
     * 删除代理
     * @param proxyId   代理id
     */
    @DeleteMapping
    public SfcJsonResult<?> deleteProxy(@RequestParam Long proxyId) {
        proxyInfoService.deleteWithOwnerPermissions(proxyId);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 获取当前用户可用的代理列表
     * @return 可用代理列表
     */
    @GetMapping("available")
    public SfcJsonResult<List<ProxyInfo>> getAvailableProxy() {
        return SfcJsonResult.getInstance(proxyInfoService.listAvailableProxy());
    }

    /**
     * 测试代理连通性
     * @param proxyId   代理id
     * @param timeout   超时（ms）
     * @param useCache  是否使用缓存结果
     * @return          是否连通，连同为true，否则为false
     */
    @GetMapping("test")
    public SfcJsonResult<Boolean> test(@RequestParam("proxyId") Long proxyId,
                                    @RequestParam(value = "timeout", defaultValue = "10000") int timeout,
                                    @RequestParam(value = "useCache",defaultValue = "true") boolean useCache
    ) {
        return SfcJsonResult.getInstance(proxyInfoService.testProxy(proxyId, timeout, useCache));
    }
}
