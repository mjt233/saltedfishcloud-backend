package com.xiaotao.saltedfishcloud.config.cache;

import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.cache.LocalCacheServiceImpl;
import com.xiaotao.saltedfishcloud.constant.CacheNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地缓存配置类。
 * 当 sys.service.cache-provider=local 时启用。
 */
@Configuration
@ConditionalOnProperty(prefix = "sys.service", name = "cache-provider", havingValue = "local")
public class LocalCacheConfig {

    /**
     * 当 sys.service.cache-provider=local 时，启用进程内缓存。
     *
     * @return 本地内存缓存管理器
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                CacheNames.PATH,
                CacheNames.DEFAULT,
                CacheNames.PROXY_TEST_RESULT,
                "cache-service-local"
        );
        cacheManager.setCacheSpecification("maximumSize=20000,expireAfterWrite=30m");
        return cacheManager;
    }

    /**
     * 当 sys.service.cache-provider=local 时，注入本地实现的缓存服务。
     *
     * @param cacheManager CacheManager Bean
     * @return 本地缓存服务实现
     */
    @Bean
    public CacheService cacheService(CacheManager cacheManager) {
        return new LocalCacheServiceImpl(cacheManager);
    }
}
