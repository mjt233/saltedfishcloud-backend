package com.saltedfishcloud.ext.minio;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Slf4j
@Data
@ConfigurationProperties("sys.store.minio")
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MinioProperties that = (MinioProperties) o;

        if (!endpoint.equals(that.endpoint)) return false;
        if (!accessKey.equals(that.accessKey)) return false;
        if (!secretKey.equals(that.secretKey)) return false;
        return bucket.equals(that.bucket);
    }

    @Override
    public int hashCode() {
        int result = endpoint.hashCode();
        result = 31 * result + accessKey.hashCode();
        result = 31 * result + secretKey.hashCode();
        result = 31 * result + bucket.hashCode();
        return result;
    }
}
