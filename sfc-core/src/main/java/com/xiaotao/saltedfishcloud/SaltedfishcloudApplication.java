package com.xiaotao.saltedfishcloud;

import com.xiaotao.saltedfishcloud.ext.DefaultPluginManager;
import com.xiaotao.saltedfishcloud.ext.DirPathClassLoader;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginProperty;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

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
        PluginManager pluginManager = new DefaultPluginManager(SaltedfishcloudApplication.class.getClassLoader());

        // 配置SpringBoot，注册插件管理器
        SpringApplication sa = new SpringApplication(SaltedfishcloudApplication.class);
        sa.addInitializers(context -> {
            context.setClassLoader(pluginManager.getJarMergeClassLoader());
            PluginProperty pluginProperty = PluginProperty.loadFromPropertyResolver(context.getEnvironment());
            try {
                initPluginFromClassPath(pluginManager);
                initPluginFromExtraResource(pluginManager, pluginProperty);
                context.addBeanFactoryPostProcessor(beanFactory -> {
                    beanFactory.registerResolvableDependency(PluginManager.class, pluginManager);
                });
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        String pluginLists = "[" + String.join(",", pluginManager.getAllPlugin().keySet()) + "]";
        log.info("[Boot]启动时加载的插件清单：{}", pluginLists);
        // 启动SpringBoot
        sa.run(args);

        // 打印启动信息
        printLaunchInfo(begin);
        System.out.println("=========咸鱼云已启动(oﾟvﾟ)ノ==========");
    }

    /**
     * 从指定的外部资源中加载插件
     */
    public static void initPluginFromExtraResource(PluginManager pluginManager, PluginProperty pluginProperty) throws IOException {
        for (String s : pluginProperty.getExtraResource()) {
            Path path = Paths.get(s).toAbsolutePath();
            PathResource pathResource = new PathResource(path);
            log.info("[Boot]从额外资源路径加载插件：{}", path);
            DirPathClassLoader classLoader = new DirPathClassLoader(path);
            pluginManager.register(pathResource, classLoader);
        }
    }

    /**
     * 从classpath中加载插件信息
     */
    public static void initPluginFromClassPath(PluginManager pluginManager) throws IOException {

        Enumeration<URL> resources = PluginManager.class.getClassLoader().getResources(PluginManager.PLUGIN_INFO_FILE);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String classpath = PathUtils.getParentPath(url.toString());
            log.info("[Boot]classpath中发现的插件：{}", classpath);
            UrlResource resource = new UrlResource(classpath);
            if ("file".equals(url.getProtocol()) && !classpath.endsWith(".jar")) {
                String classpathRoot;
                if (OSInfo.isWindows()) {
                    classpathRoot = url.getPath().substring(1);
                } else {
                    classpathRoot = url.getPath();
                }
                pluginManager.register(resource, new DirPathClassLoader(Paths.get(classpathRoot).getParent()));
            } else {
                pluginManager.register(resource);
            }
        }

        // 注册目录中的jar包插件
        for (URL extUrl : ExtUtils.getExtUrls()) {
            pluginManager.register(new UrlResource(extUrl));
        }

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
