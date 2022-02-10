package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.lang.reflect.InvocationTargetException;

@SpringBootApplication(
        exclude= {DataSourceAutoConfiguration.class, GsonAutoConfiguration.class},
        scanBasePackages = {
                "com.xiaotao.saltedfishcloud"
        }
)
@EnableTransactionManagement
@MapperScan("com.xiaotao.saltedfishcloud.dao.mybatis")
@EnableScheduling
@EnableCaching
@PropertySource({"classpath:config.properties"})
@EnableJpaAuditing
@EnableConfigurationProperties
public class SaltedfishcloudApplication {

    public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final ClassLoader extLoader = ExtUtils.loadExtJar(SaltedfishcloudApplication.class.getClassLoader());
        SpringApplication sa = new SpringApplication(SaltedfishcloudApplication.class);
        sa.setResourceLoader(new DefaultResourceLoader(extLoader));
        sa.run(args);
        System.out.println("=========咸鱼云已启动(oﾟvﾟ)ノ==========");
    }

}
