package com.sfc.staticpublish.controller;

import com.sfc.rpc.annotation.RPCResource;
import com.sfc.staticpublish.model.ServiceStatus;
import com.sfc.staticpublish.model.po.StaticPublishRecord;
import com.sfc.staticpublish.service.StaticPublishRPCClient;
import com.sfc.staticpublish.service.StaticPublishRecordService;
import com.sfc.staticpublish.service.StaticPublishService;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import org.apache.catalina.LifecycleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.RolesAllowed;
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
    public JsonResult<List<ServiceStatus>> listStatus() {
        return JsonResultImpl.getInstance(rpcClient.getStatus());
    }

    /**
     * 启动静态站点服务
     */
    @GetMapping("start")
    @RolesAllowed("ADMIN")
    public JsonResult<?> start() throws LifecycleException, IOException {
        staticPublishService.start();
        return JsonResult.emptySuccess();
    }

    /**
     * 关闭静态站点服务
     */
    @GetMapping("stop")
    @RolesAllowed("ADMIN")
    public JsonResult<?> stop() throws LifecycleException {
        staticPublishService.stop();
        return JsonResult.emptySuccess();
    }

    @PostMapping("save")
    public JsonResult<StaticPublishRecord> save(@RequestBody StaticPublishRecord record) {
        staticPublishRecordService.saveWithOwnerPermissions(record);
        return JsonResultImpl.getInstance(record);
    }

    @GetMapping("listSite")
    public JsonResult<List<StaticPublishRecord>> listSite(@RequestParam("uid") Long uid) {
        return JsonResultImpl.getInstance(staticPublishRecordService.findByUidWithOwnerPermissions(uid, null).getContent());
    }

    @PostMapping("deleteSite")
    public JsonResult<?> deleteSite(@RequestParam("id") Long id) {
        staticPublishRecordService.deleteWithOwnerPermissions(id);
        return JsonResult.emptySuccess();
    }
}
