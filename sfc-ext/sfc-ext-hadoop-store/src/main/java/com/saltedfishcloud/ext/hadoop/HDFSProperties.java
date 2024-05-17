package com.saltedfishcloud.ext.hadoop;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@ConfigurationProperties("sys.store.hdfs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class HDFSProperties {

    /**
     * master地址
     */
    private String url = "hdfs://localhost:9000";

    private String root = "/xyy";

    private String user = "xiaotao";

    public String getStoreRoot(long uid) {
        return root + "/user_file/" + uid;
    }
}
