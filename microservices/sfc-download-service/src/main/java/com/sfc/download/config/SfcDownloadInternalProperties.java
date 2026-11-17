package com.sfc.download.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本服务的内部调优配置（部署期配置，来自 {@code application.yml} 的 {@code sfc.download} 节点）。
 *
 * <p>不包含业务参数：Git 克隆默认参数与 aria2 运行参数统一由 {@link SfcDownloadConfigService}
 * 从 core 读取，本类只负责配置读取与能力探测自身的缓存时长。</p>
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
     * aria2 任务对账相关的内部参数。
     */
    private Aria2 aria2 = new Aria2();

    /**
     * P2P 任务续传/接管相关的内部参数。
     */
    private Resume resume = new Resume();

    /**
     * 统一配置读取参数。
     */
    @Data
    public static class Config {

        /**
         * 统一配置快照的默认缓存有效期（毫秒）。
         */
        public static final long DEFAULT_REFRESH_INTERVAL_MILLIS = 5000L;

        /**
         * 统一配置（Git 与 aria2 运行参数共用一份快照）的缓存有效期（毫秒）。
         *
         * <p>管理员在 core 后台修改配置后，最长经过该时长即在本服务生效（无需重启）。
         * 取值为 {@code <= 0} 时回落到默认值 5000 毫秒。</p>
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

    /**
     * aria2 孤儿任务对账参数。
     *
     * <p>对应配置前缀 {@code sfc.download.aria2}（与 aria2 部署期连接的
     * {@link Aria2Properties} 共用前缀，但本类只读取对账相关的内部参数）。</p>
     */
    @Data
    public static class Aria2 {

        /**
         * 默认的对账周期（毫秒）：5 分钟。
         */
        public static final long DEFAULT_RECONCILE_INTERVAL_MILLIS = 300_000L;

        /**
         * aria2 孤儿任务对账周期（毫秒），默认 300000（5 分钟）。
         *
         * <p>应用启动完成后会先对账一次，随后按该周期重复。取值为 {@code <= 0} 时<b>关闭</b>对账
         * （既不启动时执行，也不周期执行）；aria2 是独立守护进程，关闭后终态任务在 aria2 侧的残留
         * 与工作区目录需要由运维手工清理。</p>
         */
        private long reconcileIntervalMillis = DEFAULT_RECONCILE_INTERVAL_MILLIS;
    }

    /**
     * P2P 任务续传 / 接管参数。
     *
     * <p>对应配置前缀 {@code sfc.download.resume}。主要服务于「上传阶段幂等」：接管实例看到
     * {@code phase=UPLOADING} 的检查点时，需要判断旧实例是否可能仍在上传。</p>
     */
    @Data
    public static class Resume {

        /**
         * 默认的上传阶段陈旧阈值（毫秒）：10 分钟。
         */
        public static final long DEFAULT_UPLOAD_STALE_MILLIS = 600_000L;

        /**
         * 默认的「等待旧实例上传结果」单次等待间隔（毫秒）：10 秒。
         */
        public static final long DEFAULT_UPLOAD_WAIT_INTERVAL_MILLIS = 10_000L;

        /**
         * 默认的「等待旧实例上传结果」最大等待次数：3 次。
         */
        public static final int DEFAULT_UPLOAD_WAIT_ATTEMPTS = 3;

        /**
         * 上传阶段陈旧阈值（毫秒），默认 600000（10 分钟）。
         *
         * <p>接管实例发现 {@code phase=UPLOADING} 且 {@code now - uploadStartedAt} 小于该阈值时，认为旧实例
         * 可能仍在上传，先做有界等待；超过阈值（或等待结束仍未变成 {@code UPLOADED}）则按陈旧处理、重新上传
         * （可能产生重复文件，由 core 落盘侧的重名策略兜底）。</p>
         */
        private long uploadStaleMillis = DEFAULT_UPLOAD_STALE_MILLIS;

        /**
         * 等待旧实例上传结果的单次间隔（毫秒），默认 10000。
         */
        private long uploadWaitIntervalMillis = DEFAULT_UPLOAD_WAIT_INTERVAL_MILLIS;

        /**
         * 等待旧实例上传结果的最大次数，默认 3。
         */
        private int uploadWaitAttempts = DEFAULT_UPLOAD_WAIT_ATTEMPTS;
    }
}
