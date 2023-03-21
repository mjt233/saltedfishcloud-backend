package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.RolesAllowed;

/**
 * 集群管理器
 */
@RestController
@RequestMapping("/api/cluster")
@RolesAllowed({"ADMIN"})
public class ClusterController {
    @Autowired
    private ClusterService clusterService;

    @ApiOperation("获取集群中的所有节点")
    @GetMapping("listNodes")
    public JsonResult listNodes() {
        return JsonResultImpl.getInstance(clusterService.listNodes());
    }
}
