package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.ext.DefaultPluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.io.UrlResource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
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

    public static void main(String[] args) throws IOException {
        log.info("[Boot]程序运行目录: {}", Paths.get("").toAbsolutePath());

        long begin = System.currentTimeMillis();

        // 加载插件
        PluginManager pluginManager = initPlugin();
        Thread.currentThread().setContextClassLoader(pluginManager.getJarMergeClassLoader());

        // 配置SpringBoot，注册插件管理器
        SpringApplication sa = new SpringApplication(SaltedfishcloudApplication.class);
        sa.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            beanFactory.registerResolvableDependency(PluginManager.class, pluginManager);
        }));

        // 启动SpringBoot
        sa.run(args);

        // 打印启动信息
        printLaunchInfo(begin);


        System.out.println("=========咸鱼云已启动(oﾟvﾟ)ノ==========");
        System.gc();
    }

    public static PluginManager initPlugin() throws IOException {
        // 准备插件
        PluginManager pluginManager = new DefaultPluginManager(SaltedfishcloudApplication.class.getClassLoader());
        for (URL extUrl : ExtUtils.getExtUrls()) {
            pluginManager.register(new UrlResource(extUrl));
        }

        return pluginManager;
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
