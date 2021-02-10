package com.xiaotao.saltedfishcloud;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(exclude= {DataSourceAutoConfiguration.class})
@EnableTransactionManagement
@MapperScan("com.xiaotao.saltedfishcloud.dao")
public class SaltedfishcloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaltedfishcloudApplication.class, args);
    }

}
