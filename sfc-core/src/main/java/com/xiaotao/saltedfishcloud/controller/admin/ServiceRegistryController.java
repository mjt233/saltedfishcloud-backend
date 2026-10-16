package com.xiaotao.saltedfishcloud.controller.admin;

import com.sfc.cloud.model.ServiceInstance;
import com.sfc.cloud.registry.ServiceDiscovery;
import com.sfc.cloud.model.json.SfcJsonResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务注册与发现管理接口。
 * <p>
 * 返回的是<b>注册中心视图</b>：{@code sys.service.registry-provider: local} 时即当前进程内的注册关系，
 * 未启用微服务模式，因此只可能看到自己；集群节点列表请用 {@code GET /api/cluster/listNodes}
 * —— 两者<b>职责分离，不合并</b>。
 */
@RestController
@RequestMapping(ServiceRegistryController.PREFIX)
@RolesAllowed({"ADMIN"})
public class ServiceRegistryController {
    /** 接口路径前缀 */
    public static final String PREFIX = "/api/admin/sys/service";

    /** 服务发现 SPI，由当前生效的 provider 提供（默认进程内实现） */
    private final ServiceDiscovery serviceDiscovery;

    public ServiceRegistryController(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    /**
     * 查询服务实例列表。
     *
     * @param serviceName 服务名，留空时返回所有已知服务名下的全部实例
     * @return 服务实例列表（实例自带 serviceName），无匹配时为空列表
     */
    @Operation(summary = "获取注册中心中的服务实例列表")
    @GetMapping("list")
    public SfcJsonResult<List<ServiceInstance>> list(@RequestParam(value = "serviceName", required = false) String serviceName) {
        List<ServiceInstance> instances;
        if (serviceName == null || serviceName.isBlank()) {
            // 展平所有服务名的实例：阶段 4 的 demo 微服务接入后，同一端点即可看到多个服务
            instances = new ArrayList<>();
            for (String name : serviceDiscovery.listServiceNames()) {
                instances.addAll(serviceDiscovery.listInstances(name));
            }
        } else {
            instances = serviceDiscovery.listInstances(serviceName.trim());
        }
        return SfcJsonResult.getInstance(instances);
    }
}
