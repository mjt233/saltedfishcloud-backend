package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.Data;
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
    private Store store;
    /**
     * 系统服务相关配置。
     */
    private Service service = new Service();


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
