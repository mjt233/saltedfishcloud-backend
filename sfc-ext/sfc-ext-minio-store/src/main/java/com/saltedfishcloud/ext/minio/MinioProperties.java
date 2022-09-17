package com.saltedfishcloud.ext.minio;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Slf4j
@Data
@ConfigurationProperties("sys.store.minio")
public class MinioProperties {
    /**
     * endpoint URL地址
     */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * key
     */
    private String accessKey = "xyy";

    /**
     * 密钥
     */
    private String secretKey = "xyy123456";

    /**
     * 存储桶
     */
    private String bucket = "xyy";

}
