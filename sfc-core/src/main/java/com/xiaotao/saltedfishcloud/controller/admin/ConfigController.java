package com.xiaotao.saltedfishcloud.controller.admin;

import com.sfc.cloud.constants.SfcServiceMetaKey;
import com.sfc.common.model.PluginConfigNodeInfo;
import com.sfc.common.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import jakarta.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping(ConfigController.PREFIX)
@RolesAllowed({"ADMIN"})
@Validated
@Slf4j
@RequiredArgsConstructor
public class ConfigController {
    public static final String PREFIX = "/api/admin/sys/config";
    private final ConfigService configService;
    private final DiscoveryClient discoveryClient;

    /** 微服务调用客户端：已挂载 Spring Cloud LoadBalancer，按服务名选择真实实例 */
    private final RestClient restClient;

    /** 当前服务自身的注册名，用于从微服务列表中排除自己 */
    @Value("${spring.application.name:sfc-core}")
    private String selfServiceName;

    @GetMapping("config/{key}")
    public SfcJsonResult<String> getConfig(@PathVariable String key) {
        String res = configService.getConfig(key);
        return SfcJsonResult.getInstance(res);
    }

    @PutMapping("/batchSetConfig")
    public SfcJsonResult<?> batchSetConfig(@RequestBody List<NameValueType<String>> config) throws IOException {
        configService.batchSetConfig(config);
        return SfcJsonResult.emptySuccess();
    }


    /**
     * 设置配置项参数值
     * @param key       配置项名
     * @param value     值
     */
    @PutMapping("config/{key}/{value}")
    public SfcJsonResult<?> setConfig(@PathVariable String key, @PathVariable String  value) throws IOException {
        configService.setConfig(key, value);
        return SfcJsonResult.emptySuccess();
    }

    @GetMapping("listPluginConfig")
    public SfcJsonResult<List<PluginConfigNodeInfo>> listAllPluginConfig() {
        // 获取在当前主进程加载的插件配置面板信息
        List<PluginConfigNodeInfo> configNodeInfoList = configService.listPluginConfig();

        // 获取通过微服务注册的扩展服务配置面板信息
        configNodeInfoList.addAll(listServicesPluginConfig());
        return SfcJsonResult.getInstance(configNodeInfoList);
    }

    /**
     * 获取并汇总微服务注册的插件的配置面板信息
     */
    public List<PluginConfigNodeInfo> listServicesPluginConfig() {
        return discoveryClient.getServices()
                .parallelStream()
                // 排除自己
                .filter(serviceName -> !selfServiceName.equals(serviceName))
                // 在服务的实例列表中，随便挑选一个声明了插件信息的实例
                .map(serviceName -> discoveryClient.getInstances(serviceName)
                        .stream()
                        .filter(instance -> instance.getMetadata() != null
                                && instance.getMetadata().containsKey(SfcServiceMetaKey.PLUGIN_INFO))
                        .findAny().orElse(null))
                .filter(Objects::nonNull)
                .map(instance -> {
                    try {
                        String pluginInfoPath = instance.getMetadata().get(SfcServiceMetaKey.PLUGIN_INFO);
                        // 以「服务名」作为 host 书写目标地址，由 Spring Cloud LoadBalancer 在请求发出前
                        // 选出真实实例并重写 scheme/host/port，不再手工解析实例地址
                        String path = pluginInfoPath.startsWith("/") ? pluginInfoPath : "/" + pluginInfoPath;
                        URI uri = UriComponentsBuilder.newInstance()
                                .scheme("http")
                                .host(instance.getServiceId())
                                .path(path)
                                .build()
                                .toUri();
                        ResponseEntity<PluginConfigNodeInfo> entity = restClient.get().uri(uri)
                                .retrieve()
                                .toEntity(PluginConfigNodeInfo.class);
                        return entity.getBody();
                    } catch (RuntimeException e) {
                        log.error("获取服务实例{}:{}的插件信息失败", instance.getServiceId(), instance.getInstanceId(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
