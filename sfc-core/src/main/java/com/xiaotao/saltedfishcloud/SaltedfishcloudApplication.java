package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.init.PluginInitializer;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
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
@Slf4j
@EntityScan("com.xiaotao.saltedfishcloud.model")
@EnableJpaRepositories(basePackages = "com.xiaotao.saltedfishcloud.dao.jpa")
public class SaltedfishcloudApplication {

    public static void main(String[] args) throws IOException {

        long begin = System.currentTimeMillis();


        SpringApplication sa = new SpringApplication(SaltedfishcloudApplication.class);
        SpringContextUtils.setMainApplication(sa);
        SpringContextUtils.setLaunchArgs(args);

        // 配置SpringBoot，注册插件管理器
        sa.addInitializers(c -> log.info("[Boot]程序运行目录: {}", Paths.get("").toAbsolutePath()));
        sa.addInitializers(ctx -> new PluginInitializer().initialize(ctx));

        // 启动SpringBoot
        SpringContextUtils.setContext(sa.run(args));

        // 打印启动信息
        printLaunchInfo(begin);
        System.out.println("=========咸鱼云已启动(oﾟvﾟ)ノ==========");
    }

    public static void printLaunchInfo(long beginTime) {

        Runtime runtime = Runtime.getRuntime();
        long total = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        log.info("启动耗时：{}s 最大内存：{} 占用内存：{} 使用率：{}%",
                (System.currentTimeMillis() - beginTime) / 1000.0,
                StringUtils.getFormatSize(total),
                StringUtils.getFormatSize(used),
                (used * 100 / total)
        );
    }

}
