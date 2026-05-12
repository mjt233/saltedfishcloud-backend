package com.xiaotao.saltedfishcloud.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;

import java.util.Set;

/**
 * 根据锁实现配置控制 Redisson 自动配置是否导入。
 * <p>当 {@code sys.service.lock-provider=local} 时，排除 Redisson Starter 的自动配置，避免注入 Redisson 相关 Bean。</p>
 */
@SuppressWarnings("unused")
public class LockProviderAutoConfigurationImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    /**
     * Redisson Starter 暴露的自动配置类全限定名集合。
     */
    private static final Set<String> REDISSON_AUTO_CONFIGURATION_CLASSES = Set.of(
            "org.redisson.spring.starter.RedissonAutoConfiguration",
            "org.redisson.spring.starter.RedissonAutoConfigurationV2"
    );

    /**
     * 当前 Spring 运行环境。
     */
    private Environment environment;

    /**
     * 根据锁实现配置决定候选自动配置是否应被导入。
     *
     * @param autoConfigurationClasses 候选自动配置类全限定名列表
     * @param autoConfigurationMetadata 自动配置元数据
     * @return 与候选自动配置列表顺序一致的匹配结果数组
     */
    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean useLocalLockProvider = isLocalLockProvider();
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        if (!useLocalLockProvider) {

            for (int i = 0; i < autoConfigurationClasses.length; i++) {
                matches[i] = true;
            }
            return matches;
        }


        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            if (autoConfigurationClasses[i] != null) {
                boolean isRedissonConfiguration = REDISSON_AUTO_CONFIGURATION_CLASSES.contains(autoConfigurationClasses[i]);
                matches[i] = !isRedissonConfiguration;
            } else {
                matches[i] = true;
            }
        }
        return matches;
    }

    /**
     * 设置当前 Spring 运行环境。
     *
     * @param environment Spring 运行环境
     */
    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    /**
     * 判断当前是否启用了本地锁实现。
     *
     * @return true 表示使用本地锁；false 表示继续使用 Redisson 锁
     */
    private boolean isLocalLockProvider() {
        return "local".equalsIgnoreCase(environment == null ? "redisson" : environment.getProperty("sys.service.lock-provider", "redisson"));
    }
}

