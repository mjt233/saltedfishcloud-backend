package com.xiaotao.saltedfishcloud;

import com.sfc.archive.annocation.EnableArchive;
import com.sfc.archive.service.DiskFileSystemArchiveService;
import com.sfc.rpc.annotation.EnableRpc;
import com.sfc.task.annocation.EnableAsyncTask;
import com.xiaotao.saltedfishcloud.init.PluginInitializer;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import emergency.EmergencyApplication;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Indexed;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.nio.file.Paths;
import java.util.Date;
import java.util.function.Supplier;

@SpringBootApplication(
        exclude= {GsonAutoConfiguration.class},
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
@EnableAsync
@EnableAsyncTask
@EnableArchive
@EnableRpc
@Indexed
public class SaltedfishcloudApplication {

    public static void main(String[] args) {
        // 记录启动参数
        SpringContextUtils.setLaunchArgs(args);

        // 设置启动程序工厂
        SpringContextUtils.setApplicationFactory(getLaunchFactory());

        // 设置紧急模式启动程序工厂
        SpringContextUtils.setEmergencyApplicationFactory(getEmergencyModeLaunchFactory());

        try {
            // 启动
            ConfigurableApplicationContext context = getLaunchFactory().get().run(args);

            // 记录上下文
            SpringContextUtils.setContext(context);
        } catch (Exception ignore) {}
    }

    public static Supplier<SpringApplication> getEmergencyModeLaunchFactory() {
        return () -> new SpringApplication(EmergencyApplication.class);
    }

    public static Supplier<SpringApplication> getLaunchFactory() {
        return () -> {

            long begin = System.currentTimeMillis();


            SpringApplication sa = new SpringApplication(SaltedfishcloudApplication.class);

            // 配置SpringBoot，注册插件管理器
            sa.addInitializers(c -> log.info("[Boot]程序运行目录: {}", Paths.get("").toAbsolutePath()));
            sa.addInitializers(new PluginInitializer());

            sa.addListeners((ApplicationListener<ApplicationReadyEvent>) applicationEvent -> {
                // 打印启动信息
                printLaunchInfo(begin);
                System.out.println("=========咸鱼云已启动(oﾟvﾟ)ノ==========");
                EmergencyApplication.throwable = null;
                EmergencyApplication.errorDate = null;
            });

            sa.addListeners((ApplicationListener<ApplicationFailedEvent>) applicationEvent -> {
                log.error("[Boot]启动失败",applicationEvent.getException());
                applicationEvent.getApplicationContext().close();

                // 启动紧急模式
                EmergencyApplication.throwable = applicationEvent.getException();
                EmergencyApplication.errorDate = new Date();
                new Thread(SpringContextUtils::startEmergencyMode).start();
            });
            return sa;
        };
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
