package com.xiaotao.saltedfishcloud;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(
        exclude= {DataSourceAutoConfiguration.class},
        scanBasePackages = {
                "com.xiaotao.saltedfishcloud",
                "com.xiaotao.saltedfishcloud.ext"
        }
)
@EnableTransactionManagement
@MapperScan("com.xiaotao.saltedfishcloud.dao.mybatis")
@EnableScheduling
@EnableCaching
@PropertySource({"classpath:config.properties", "classpath:sysConfig.yml"})
@EnableJpaAuditing
public class SaltedfishcloudApplication {

    public static void main(String[] args) {
        SpringApplication sa = new SpringApplication(SaltedfishcloudApplication.class);
        sa.run(args);
        System.out.println("=========咸鱼云已启动(oﾟvﾟ)ノ==========");
    }

}
