package com.saltedfishcloud.ext.minio;

import com.saltedfishcloud.ext.minio.utils.MinioUtils;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(prefix = "sys.store", name = "type", havingValue = "minio")
@EnableConfigurationProperties(MinioProperties.class)
@Configuration
@Slf4j
public class MinioStoreAutoConfiguration {
    @Autowired
    private MinioProperties minioProperties;

    @Autowired
    private FileResourceMd5Resolver md5Resolver;

    @Autowired
    private SysProperties sysProperties;

    @Bean
    public MinioStoreService minioStoreService() {
        return new MinioStoreService(new MinioDirectRawHandler(minioClient(), minioProperties), md5Resolver);
    }

    @Bean
    public StoreServiceFactory storeServiceProvider() {
        return () -> {
            if (sysProperties.getStore().getMode() == StoreMode.RAW) {
                return this.minioStoreService().getRawStoreService();
            } else {
                return this.minioStoreService().getUniqueStoreService();
            }
        };
    }

    @Bean
    public MinioClient minioClient() {
        log.info("[MinioStore]初始化minio客户端，endpoint:{} bucket:{}", minioProperties.getEndpoint(), minioProperties.getBucket());
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        try {
            if(!client.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build())) {
                log.info("[MinioStore]初始化桶：{}", minioProperties.getBucket());
                client.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
            }
        } catch (Exception e) {
            if (MinioUtils.isInvalidAccessKeyId(e)) {
                log.error("[MinioStore]认证失败，请检查sys.store.minio.accessKey和sys.store.minio.secretKey");
                throw new RuntimeException("[MinioStore]认证失败，请检查sys.store.minio.accessKey和sys.store.minio.secretKey");
            } else {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return client;
    }
}
