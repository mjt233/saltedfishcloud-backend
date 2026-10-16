package com.xiaotao.saltedfishcloud.controller.admin;

import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.sfc.cloud.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.service.ClusterService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.security.RolesAllowed;
import java.util.List;

/**
 * 集群管理器
 */
@RestController
@RequestMapping("/api/cluster")
@RolesAllowed({"ADMIN"})
public class ClusterController {
    @Autowired
    private ClusterService clusterService;

    @Operation(summary = "获取集群中的所有节点")
    @GetMapping("listNodes")
    public SfcJsonResult<List<ClusterNodeInfo>> listNodes() {
        return SfcJsonResult.getInstance(clusterService.listNodes());
    }
}