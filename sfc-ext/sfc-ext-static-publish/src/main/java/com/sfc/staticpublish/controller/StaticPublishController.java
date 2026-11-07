package com.sfc.staticpublish.controller;

import com.sfc.rpc.annotation.RPCResource;
import com.sfc.staticpublish.model.ServiceStatus;
import com.sfc.staticpublish.model.po.StaticPublishRecord;
import com.sfc.staticpublish.service.StaticPublishRPCClient;
import com.sfc.staticpublish.service.StaticPublishRecordService;
import com.sfc.staticpublish.service.StaticPublishService;
import com.sfc.common.model.json.SfcJsonResult;
import org.apache.catalina.LifecycleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.security.RolesAllowed;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/staticPublish")
public class StaticPublishController {
    @Autowired
    private StaticPublishService staticPublishService;

    @Autowired
    private StaticPublishRecordService staticPublishRecordService;

    @RPCResource
    private StaticPublishRPCClient rpcClient;

    /**
     * 获取所有节点的服务运行状态
     */
    @GetMapping("listStatus")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<List<ServiceStatus>> listStatus() {
        return SfcJsonResult.getInstance(rpcClient.getStatus());
    }

    /**
     * 启动静态站点服务
     */
    @GetMapping("start")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<?> start() throws LifecycleException, IOException {
        staticPublishService.start();
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 关闭静态站点服务
     */
    @GetMapping("stop")
    @RolesAllowed("ADMIN")
    public SfcJsonResult<?> stop() throws LifecycleException {
        staticPublishService.stop();
        return SfcJsonResult.emptySuccess();
    }

    @PostMapping("save")
    public SfcJsonResult<StaticPublishRecord> save(@RequestBody StaticPublishRecord record) {
        staticPublishRecordService.saveWithOwnerPermissions(record);
        return SfcJsonResult.getInstance(record);
    }

    @GetMapping("listSite")
    public SfcJsonResult<List<StaticPublishRecord>> listSite(@RequestParam("uid") Long uid) {
        return SfcJsonResult.getInstance(staticPublishRecordService.findByUidWithOwnerPermissions(uid, null).getContent());
    }

    @PostMapping("deleteSite")
    public SfcJsonResult<?> deleteSite(@RequestParam("id") Long id) {
        staticPublishRecordService.deleteWithOwnerPermissions(id);
        return SfcJsonResult.emptySuccess();
    }
}
