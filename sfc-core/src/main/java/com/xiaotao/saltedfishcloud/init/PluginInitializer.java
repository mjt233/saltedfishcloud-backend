package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.SaltedfishcloudApplication;
import com.xiaotao.saltedfishcloud.ext.DefaultPluginManager;
import com.xiaotao.saltedfishcloud.ext.DirPathClassLoader;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginProperty;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

@Slf4j
public class PluginInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private final static String LOG_PREFIX = "[插件初始化]";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        long begin = System.currentTimeMillis();
        // 加载插件
        PluginManager pluginManager = new DefaultPluginManager(SaltedfishcloudApplication.class.getClassLoader());
        context.setClassLoader(pluginManager.getJarMergeClassLoader());
        PluginProperty pluginProperty = PluginProperty.loadFromPropertyResolver(context.getEnvironment());
        try {
            initPluginFromClassPath(pluginManager);
            initPluginFromExtraResource(pluginManager, pluginProperty);
            context.addBeanFactoryPostProcessor(beanFactory -> {
                beanFactory.registerResolvableDependency(PluginManager.class, pluginManager);
            });
            String pluginLists = "[" + String.join(",", pluginManager.getAllPlugin().keySet()) + "]";
            log.info("{}启动时加载的插件清单：{}",LOG_PREFIX, pluginLists);
            log.info("{}插件初始化耗时：{}s",LOG_PREFIX, (System.currentTimeMillis() - begin)/1000d);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }



    /**
     * 从指定的外部资源中加载插件
     */
    public static void initPluginFromExtraResource(PluginManager pluginManager, PluginProperty pluginProperty) throws IOException {
        for (String s : pluginProperty.getExtraResource()) {
            Path path = Paths.get(s).toAbsolutePath();

            // 检测是否为已存在编译结果的项目代码目录，如果是则使用编译输出目录
            Path outputDir = path.resolve("target/classes");
            if (Files.exists(outputDir) && Files.isDirectory(outputDir)) {
                path = outputDir;
            }

            PathResource pathResource = new PathResource(path);
            log.info("{}从额外资源路径加载插件：{}",LOG_PREFIX, path);
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
            log.info("{}classpath中发现的插件：{}",LOG_PREFIX, classpath);
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
}
