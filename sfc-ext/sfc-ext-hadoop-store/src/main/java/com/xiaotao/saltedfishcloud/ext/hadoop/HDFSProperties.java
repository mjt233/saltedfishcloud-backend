package com.xiaotao.saltedfishcloud.ext.hadoop;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:/hadoop.yml")
@ConfigurationProperties("store.hdfs")
@Slf4j
@Data
public class HDFSProperties implements InitializingBean {

    /**
     * master地址
     */
    @Value("${url}")
    private String url;

    @Value("${root}")
    private String root;

    @Value("${user}")
    private String user;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("[HDFS]URL:\t{}", url);
        log.info("[HDFS]ROOT:\t{}", root);
        log.info("[HDFS]USER:\t{}", user);
    }

    public String getStoreRoot(int uid) {
        return root + "/private/" + uid;
    }

    public String getUserProfileRoot(int uid) {
        return root + "/profile/" + uid;
    }
}
