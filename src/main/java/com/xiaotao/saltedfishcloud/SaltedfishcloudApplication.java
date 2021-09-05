package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.utils.SpringContextHolder;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(exclude= {DataSourceAutoConfiguration.class})
@EnableTransactionManagement
@MapperScan("com.xiaotao.saltedfishcloud.dao.mybatis")
@EnableScheduling
@EnableCaching
@PropertySource("classpath:config.properties")
@EnableJpaAuditing
public class SaltedfishcloudApplication {

    public static void main(String[] args) {
        SpringApplication sa = new SpringApplication(SaltedfishcloudApplication.class);
        SpringContextHolder.setContext(sa.run(args));

    }

}
