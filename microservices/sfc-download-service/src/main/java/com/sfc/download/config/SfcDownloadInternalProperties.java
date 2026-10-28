package com.sfc.download.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本服务的内部调优配置（部署期配置，来自 {@code application.yml} 的 {@code sfc.download} 节点）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sfc.download")
public class SfcDownloadInternalProperties {

    /**
     * 统一配置读取相关的内部参数。
     */
    private Config config = new Config();

    /**
     * 下载类型能力探测相关的内部参数。
     */
    private Capability capability = new Capability();

    /**
     * 统一配置读取参数。
     */
    @Data
    public static class Config {

        /**
         * 统一配置快照的缓存有效期（毫秒）。
         *
         * <p>管理员在 core 后台修改配置后，最长经过该时长即在本服务生效（无需重启）。
         * 取值为 {@code <= 0} 时回落到默认值 5000 毫秒。</p>
         */
        public static final long DEFAULT_REFRESH_INTERVAL_MILLIS = 5000L;

        /**
         * 配置快照缓存有效期（毫秒），默认 5 秒。
         */
        private long refreshIntervalMillis = DEFAULT_REFRESH_INTERVAL_MILLIS;
    }

    /**
     * 能力探测参数。
     */
    @Data
    public static class Capability {

        /**
         * 默认的能力探测结果缓存时长（秒）。
         *
         * <p>取值 {@code <= 0} 时回落到默认值 30 秒。</p>
         */
        public static final long DEFAULT_CACHE_SECONDS = 30L;

        /**
         * 能力探测结果缓存时长（秒），默认 30 秒。
         */
        private long cacheSeconds = DEFAULT_CACHE_SECONDS;
    }
}
