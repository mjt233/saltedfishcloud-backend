package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.ext.ExtJarClassLoader;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.nio.file.Paths;

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
@EnableJpaAuditing
@EnableConfigurationProperties
//@EnableOrmConfig
@Slf4j
public class SaltedfishcloudApplication {

    public static void main(String[] args) {
        long begin = System.currentTimeMillis();
        log.info("[Boot]程序运行目录: {}", Paths.get("").toAbsolutePath());
        final ExtJarClassLoader loader = new ExtJarClassLoader(SaltedfishcloudApplication.class.getClassLoader());
        loader.loadAll();
        Thread.currentThread().setContextClassLoader(loader);
        SpringApplication sa = new SpringApplication(SaltedfishcloudApplication.class);
        sa.run(args);

        Runtime runtime = Runtime.getRuntime();
        long total = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        log.info("启动耗时：{}s 最大内存：{} 占用内存：{} 使用率：{}%",
                (System.currentTimeMillis() - begin) / 1000.0,
                StringUtils.getFormatSize(total),
                StringUtils.getFormatSize(used),
                (used * 100 / total)
        );
        System.out.println("=========咸鱼云已启动(oﾟvﾟ)ノ==========");
        System.gc();
    }

}
