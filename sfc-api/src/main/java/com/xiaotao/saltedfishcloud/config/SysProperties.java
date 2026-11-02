package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "sys")
@Data
public class SysProperties implements InitializingBean {


    @Value("${app.version}")
    private Version version;

    /**
     * 构建日期（格式：yyyyMMddHHmmss），由 Maven 在打包时通过资源过滤注入
     */
    @Value("${app.build-date}")
    private String buildDate;

    /**
     * JWT 会话密钥（配置项 {@code sys.jwt-secret}，推荐通过环境变量 {@code SFC_JWT_SECRET} 注入）。
     * <p>用于用户登录凭证与文件直链的签名/验签，必须恰好 32 个字符；未配置或长度不符时应用启动失败，
     * 避免与网关（sfc-gateway）使用不同密钥导致验签漂移。密钥原文不允许打印到日志。</p>
     */
    @ToString.Exclude
    private String JWT_SECRET;

    /**
     * Redis 相关配置。
     */
    private Redis redis = new Redis();

    private Store store;
    /**
     * 系统服务相关配置。
     */
    private Service service = new Service();


    /**
     * Redis 相关配置。
     */
    @Data
    public static class Redis {
        /**
         * 是否启用 Redis，配置项 {@code sys.redis.enabled}。
         * <p>默认值为 {@code true}；设置为 {@code false} 时允许在不启动 Redis 的情况下运行系统。</p>
         * <p>注意：禁用 Redis 会影响缓存（{@code sys.service.cache-provider} 需设置为 {@code local}）、
         * 资源锁（{@code sys.service.lock-provider} 需设置为 {@code local}）、消息队列
         * （{@code sys.service.mq-provider} 需设置为 {@code local}）以及 RPC 等功能。</p>
         */
        private boolean enabled = true;
    }

    @Data
    public static class Store {
        /**
         * 本地文件系统中的临时目录
         */
        private String localTempDir = PathUtils.getTempDirectory();

        /**
         * 压缩文件操作使用的文件编码格式，默认使用系统默认编码，Linux: UTF-8，Windows: GBK
         */
        private String archiveEncoding = OSInfo.getOSDefaultEncoding();

        /**
         * 存储服务类型，可选hdfs或local
         */
        private String type = "local";

        /**
         * 各类资源根目录存储路径
         */
        private String root = "store";

        /**
         * 公共网盘根目录路径
         */
        private String publicRoot = "public";

        public void setArchiveEncoding(String encoding) {
            if (null == encoding || encoding.trim().isEmpty()) {
                this.archiveEncoding = OSInfo.getOSDefaultEncoding();
                log.info("未设置环境变量 ${ARCHIVE_ENCODING}, sys.store.archiveEncoding 取默认值 {}", this.archiveEncoding);
            } else {
                Charset.forName(encoding);
                this.archiveEncoding = encoding;
            }
        }
    }

    @Data
    public static class Service {
        /**
         * 缓存服务提供者，配置项 {@code sys.service.cache-provider}。<br>
         * redis：使用 Redis 实现缓存（默认值）；<br>
         * local：使用进程内本地缓存。
         */
        private String cacheProvider = "redis";

        /**
         * 资源锁服务提供者，配置项 {@code sys.service.lock-provider}。<br>
         * redisson：使用 Redisson 实现分布式资源锁（默认值）；<br>
         * local：使用仅当前服务实例内生效的本地可重入锁（不注入 Redisson）。
         */
        private String lockProvider = "redisson";

        /**
         * 消息队列服务提供者，可选 redis 或 local。
         */
        private String mqProvider = "redis";

        /**
         * RPC 服务提供者，可选 redis 或 mq。
         */
        private String rpcProvider = "redis";
    }


    public void setVersion(String v) {
        version = Version.valueOf(v);
    }


    /*
     * 检查公共网盘路径和私人存储路径是否存在冲突。<br>
     * 以下情况会被认为冲突：<br>
     * <ul>
     *  <li>当公共网盘路径或私人网盘路径为父子目录关系</li>
     *  <li>公共网盘路径与私人网盘路径为相同的目录</li>
     * </ul>
     */
    public void checkPathConflict() {
        if (
                PathUtils.isSubDir(store.publicRoot, store.root) ||
                        PathUtils.isSubDir(store.root, store.publicRoot) ||
                        store.root.equals(store.publicRoot)
        ) {
            throw new IllegalArgumentException("公共网盘路径与私人网盘路径冲突（父子关系或相等）");
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.checkPathConflict();
    }
}
