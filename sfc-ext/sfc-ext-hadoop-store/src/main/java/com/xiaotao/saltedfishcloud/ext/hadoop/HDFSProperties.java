package com.xiaotao.saltedfishcloud.ext.hadoop;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Configuration
@ConditionalOnProperty(prefix = "sys.store", name = "type", havingValue = "hdfs")
@ConfigurationProperties("sys.store.hdfs")
@Slf4j
@Data
public class HDFSProperties implements InitializingBean {

    /**
     * master地址
     */
    private String url = "hdfs://localhost:9000";

    private String root = "/xyy";

    private String user = "xiaotao";

    @Override
    public void afterPropertiesSet() {
        log.info("[HDFS]URL :{}", url);
        log.info("[HDFS]ROOT:{}", root);
        log.info("[HDFS]USER:{}", user);
    }

    public String getStoreRoot(int uid) {
        return root + "/private/" + uid;
    }

    public String getUserProfileRoot(int uid) {
        return root + "/profile/" + uid;
    }
}
