package com.xiaotao.saltedfishcloud.service.registry;

import com.sfc.cloud.model.ServiceDefinition;
import com.sfc.cloud.model.ServiceInstance;
import com.sfc.cloud.registry.ServiceRegistry;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.sfc.cloud.exception.SfcJsonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 服务注册初始化器：容器就绪后把本实例登记到 {@link ServiceRegistry}。
 * <p>
 * <b>为什么监听 {@link ApplicationReadyEvent} 而不是实现 {@code InitializingBean}</b>：现有 {@code ClusterServiceImpl}
 * 在 {@code afterPropertiesSet()} 就注册，是因为它只读配置属性 {@code server.port}；而本类要支持 {@code server.port: 0}
 * （随机端口），必须等 web 容器真正绑定端口后才能拿到真实端口，因此时机只能推到 {@code ApplicationReadyEvent}。
 * 代价是"启动完成 → 注册完成"之间存在一个极短的窗口，可接受。
 * <p>
 * <b>为什么不做注销</b>：注销时机属于 provider 自身的职责——{@code local} 进程即生命周期，{@code nacos} 由插件实现
 * {@code DisposableBean} 并在其中调用 {@code deregister()}。也正因为如此，不能把注销寄托在 {@code Closeable.close()} 上：
 * {@code @Component} 扫描得到的 Bean，Spring 并不保证推断并调用 {@code close()}。
 */
@Slf4j
@Component
public class ServiceRegistryInitializer {
    /**
     * SPI 提供者。使用 {@link ObjectProvider} 而非直接 {@code @Autowired ServiceRegistry}，
     * 是为了在"配置了 provider 却没有任何实现"时给出明确提示，而不是抛出晦涩的 {@code NoSuchBeanDefinitionException}。
     * <p>
     * 注意：插件提供的 Bean 由 {@code AutoConfiguration.imports} 注册、晚于 {@code @Component} 扫描，
     * 这会让 {@code @ConditionalOnBean} 之类的条件评估看不到它，但<b>不影响本类的依赖注入</b>。
     */
    private final ObjectProvider<ServiceRegistry> serviceRegistryProvider;

    /** 身份解析器：解析本机对外地址与端口 */
    private final ServiceIdentityResolver identityResolver;

    /** 系统配置：提供 serviceName / registryGroup / registryProvider */
    private final SysProperties sysProperties;

    public ServiceRegistryInitializer(ObjectProvider<ServiceRegistry> serviceRegistryProvider,
                                     ServiceIdentityResolver identityResolver,
                                     SysProperties sysProperties) {
        this.serviceRegistryProvider = serviceRegistryProvider;
        this.identityResolver = identityResolver;
        this.sysProperties = sysProperties;
    }

    /**
     * 容器就绪后解析自身身份并注册。使用 {@code @EventListener} 而非实现 {@code ApplicationListener}，
     * 与本项目既有的 {@code @EventListener(ApplicationReadyEvent.class)} 写法保持一致；
     * 事件本身不需要读取，故方法不声明参数。
     *
     * @throws SfcJsonException 配置的 provider 没有对应实现时抛出（见 {@link #serviceRegistryProvider}）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerSelf() {
        ServiceRegistry registry = serviceRegistryProvider.getIfAvailable();
        if (registry == null) {
            String provider = sysProperties.getService().getRegistryProvider();
            throw new SfcJsonException("未找到 ServiceRegistry 实现：sys.service.registry-provider = " + provider
                    + "。使用 nacos 需安装并加载 sfc-ext-nacos 插件；否则请把该项配置为 local");
        }
        ServiceDefinition definition = ServiceDefinition.builder()
                .serviceName(sysProperties.getService().getServiceName())
                .group(sysProperties.getService().getRegistryGroup())
                // namespace 留空：进程内注册表即单一命名空间，该字段是 Nacos 模式的概念
                .namespace(null)
                .metadata(null)
                .build();
        ServiceInstance instance = identityResolver.resolve();
        registry.register(definition, instance);
        log.info("[服务注册] 注册完成 provider={} serviceName={} group={} instanceId={} available={}",
                sysProperties.getService().getRegistryProvider(), definition.getServiceName(),
                definition.getGroup(), instance.getInstanceId(), registry.isAvailable());
    }
}
