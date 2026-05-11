package com.xiaotao.saltedfishcloud.config.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存配置属性。
 */
@Data
@ConfigurationProperties(prefix = "sys.service.local-cache")
public class LocalCacheProperty {

    /**
     * 默认缓存过期时间（毫秒）。
     * 小于等于 0 时表示永不过期。
     */
    private long defaultExpireMs = TimeUnit.MINUTES.toMillis(15);

    /**
     * 本地缓存最大数量。
     * 小于等于 0 时表示不限制。
     */
    private int maxCacheSize = 4096;
}

