package com.xiaotao.saltedfishcloud.ext;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:/hadoop.yml")
@ConfigurationProperties("store.hadoop")
@Data
public class HadoopStoreProperties implements InitializingBean {

    /**
     * master地址
     */
    @Value("${master}")
    private String master;

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("Hadoop master: " + master);
    }
}
