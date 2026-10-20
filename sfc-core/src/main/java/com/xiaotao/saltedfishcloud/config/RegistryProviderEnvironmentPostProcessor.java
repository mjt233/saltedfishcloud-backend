package com.xiaotao.saltedfishcloud.config;

import com.sfc.common.exception.SfcJsonException;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 服务注册 provider 开关：把 {@code sys.service.registry-provider} 派生为
 * Spring Cloud Alibaba 的服务发现开关。
 * <p>
 * Nacos 自动配置统一以 {@code spring.cloud.nacos.discovery.enabled} 作为条件，
 * 而系统对外的唯一开关是 {@code sys.service.registry-provider}。本处理器在配置文件加载完成后
 * 以最高优先级写入派生值，保证 {@code local} 模式不会创建 Nacos 客户端，{@code nacos} 模式无需额外开关。
 * <p>
 * 处理器必须晚于 ConfigData 加载 profile 配置，因此使用 {@link Ordered#LOWEST_PRECEDENCE}。
 */
public class RegistryProviderEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /** 服务注册 provider 配置项 */
    public static final String PROVIDER_PROPERTY = "sys.service.registry-provider";

    /** Nacos 服务发现开关配置项 */
    public static final String NACOS_DISCOVERY_ENABLED_PROPERTY = "spring.cloud.nacos.discovery.enabled";

    /** 默认 provider */
    private static final String DEFAULT_PROVIDER = "local";

    /** 进程内注册表 provider */
    private static final String LOCAL_PROVIDER = "local";

    /** Nacos 注册中心 provider */
    private static final String NACOS_PROVIDER = "nacos";

    /** 派生属性源名称 */
    private static final String PROPERTY_SOURCE_NAME = "sfcRegistryProvider";

    /**
     * 校验 provider 配置并派生 Nacos 服务发现开关。
     *
     * @param environment 当前运行环境
     * @param application Spring 应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String provider = environment.getProperty(PROVIDER_PROPERTY, DEFAULT_PROVIDER);
        provider = provider.isBlank() ? DEFAULT_PROVIDER : provider.trim().toLowerCase(Locale.ROOT);
        if (!LOCAL_PROVIDER.equals(provider) && !NACOS_PROVIDER.equals(provider)) {
            throw new SfcJsonException("sys.service.registry-provider 仅支持 local 或 nacos，当前值：" + provider);
        }

        Map<String, Object> derived = new HashMap<>(2);
        // 回写归一化后的值，保证 @ConditionalOnProperty 与配置绑定使用同一种大小写
        derived.put(PROVIDER_PROPERTY, provider);
        derived.put(NACOS_DISCOVERY_ENABLED_PROPERTY, Boolean.toString(NACOS_PROVIDER.equals(provider)));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, derived));
    }

    /**
     * 在 ConfigData 加载完 profile 配置后执行。
     *
     * @return 最低优先级
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
