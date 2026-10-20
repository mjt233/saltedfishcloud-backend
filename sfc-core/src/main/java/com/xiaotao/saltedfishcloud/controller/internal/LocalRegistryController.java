package com.xiaotao.saltedfishcloud.controller.internal;

import com.sfc.cloud.local.registry.SfcLocalRegistryConstants;
import com.sfc.cloud.local.registry.model.SfcLocalInstanceRef;
import com.sfc.cloud.local.registry.model.SfcLocalRegisterRequest;
import com.sfc.cloud.local.registry.model.SfcLocalRegisterResult;
import com.sfc.cloud.local.registry.model.SfcLocalServiceInstance;
import com.sfc.common.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.service.registry.local.LocalRegistryAuthService;
import com.xiaotao.saltedfishcloud.service.registry.local.LocalServiceRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * local 注册中心外部服务接口。
 * <p>
 * 仅在 {@code sys.service.registry-provider=local} 时装配，所有请求都必须携带
 * {@code X-Sfc-Local-Registry-Password} 请求头。该接口独立于 {@code /api/internal/**}，
 * 不会被服务间鉴权过滤器拦截；客户端应直连 core。
 */
@RestController
@RequestMapping(SfcLocalRegistryConstants.API_PREFIX)
@ConditionalOnProperty(prefix = "sys.service", name = "registry-provider",
        havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
@Tag(name = "本地注册中心-内部接口")
public class LocalRegistryController {
    /** 注册中心 */
    private final LocalServiceRegistry localServiceRegistry;

    /** 密码校验服务 */
    private final LocalRegistryAuthService authService;

    /**
     * 注册外部服务实例。
     *
     * @param password 注册密码
     * @param request  注册请求
     * @return 实例 ID 与心跳超时时间
     */
    @PostMapping(SfcLocalRegistryConstants.PATH_REGISTER)
    @AllowAnonymous
    public SfcJsonResult<SfcLocalRegisterResult> register(
            @RequestHeader(value = SfcLocalRegistryConstants.PASSWORD_HEADER, required = false) String password,
            @RequestBody SfcLocalRegisterRequest request) {
        authService.verify(password);
        return SfcJsonResult.getInstance(localServiceRegistry.registerExternal(request));
    }

    /**
     * 外部服务实例心跳续约。
     *
     * @param password 注册密码
     * @param request  实例引用
     * @return 心跳超时秒数
     */
    @PostMapping(SfcLocalRegistryConstants.PATH_HEARTBEAT)
    @AllowAnonymous
    public SfcJsonResult<Long> heartbeat(
            @RequestHeader(value = SfcLocalRegistryConstants.PASSWORD_HEADER, required = false) String password,
            @RequestBody SfcLocalInstanceRef request) {
        authService.verify(password);
        return SfcJsonResult.getInstance(
                localServiceRegistry.heartbeatExternal(request.getServiceId(), request.getInstanceId()));
    }

    /**
     * 注销外部服务实例。
     *
     * @param password 注册密码
     * @param request  实例引用
     * @return 空成功响应
     */
    @PostMapping(SfcLocalRegistryConstants.PATH_DEREGISTER)
    @AllowAnonymous
    public SfcJsonResult<Void> deregister(
            @RequestHeader(value = SfcLocalRegistryConstants.PASSWORD_HEADER, required = false) String password,
            @RequestBody SfcLocalInstanceRef request) {
        authService.verify(password);
        localServiceRegistry.deregisterExternal(request.getServiceId(), request.getInstanceId());
        return SfcJsonResult.getInstance(null);
    }

    /**
     * 查询指定服务的实例列表。
     *
     * @param password  注册密码
     * @param serviceId 服务名
     * @return 实例列表
     */
    @GetMapping(SfcLocalRegistryConstants.PATH_INSTANCES)
    @AllowAnonymous
    public SfcJsonResult<List<SfcLocalServiceInstance>> instances(
            @RequestHeader(value = SfcLocalRegistryConstants.PASSWORD_HEADER, required = false) String password,
            @RequestParam("serviceId") String serviceId) {
        authService.verify(password);
        List<SfcLocalServiceInstance> instances = localServiceRegistry.getInstances(serviceId)
                .stream()
                .map(LocalRegistryController::toDto)
                .toList();
        return SfcJsonResult.getInstance(instances);
    }

    /**
     * 查询所有已注册的服务名。
     *
     * @param password 注册密码
     * @return 服务名列表
     */
    @GetMapping(SfcLocalRegistryConstants.PATH_SERVICES)
    @AllowAnonymous
    public SfcJsonResult<List<String>> services(
            @RequestHeader(value = SfcLocalRegistryConstants.PASSWORD_HEADER, required = false) String password) {
        authService.verify(password);
        return SfcJsonResult.getInstance(localServiceRegistry.getServices());
    }

    /**
     * 把 Spring Cloud 实例转换为注册中心接口 DTO。
     *
     * @param instance 实例
     * @return 接口 DTO
     */
    private static SfcLocalServiceInstance toDto(ServiceInstance instance) {
        SfcLocalServiceInstance dto = new SfcLocalServiceInstance();
        dto.setServiceId(instance.getServiceId());
        dto.setInstanceId(instance.getInstanceId());
        dto.setHost(instance.getHost());
        dto.setPort(instance.getPort());
        dto.setSecure(instance.isSecure());
        dto.setMetadata(instance.getMetadata());
        return dto;
    }
}
