package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.ext.DefaultPluginManager;
import com.xiaotao.saltedfishcloud.ext.DirPathClassLoader;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.ext.PluginProperty;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
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
import java.util.List;

@Slf4j
public class PluginInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private final static String LOG_PREFIX = "[插件初始化]";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        long begin = System.currentTimeMillis();
        // 加载插件
        PluginManager pluginManager = new DefaultPluginManager(PluginInitializer.class.getClassLoader());
        context.setClassLoader(pluginManager.getJarMergeClassLoader());
        PluginProperty pluginProperty = PluginProperty.loadFromPropertyResolver(context.getEnvironment());
        try {
            initBuildInPlugin(pluginManager);
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
    public void initPluginFromExtraResource(PluginManager pluginManager, PluginProperty pluginProperty) throws IOException {
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
     * 初始化内置的插件信息
     */
    public void initBuildInPlugin(PluginManager pluginManager) throws IOException {
        String buildInPath = "build-in-plugin";
        ClassLoader loader = PluginInitializer.class.getClassLoader();
        PluginInfo pluginInfo = MapperHolder.parseJson(ExtUtils.getResourceText(loader, buildInPath + "/" + PluginManager.PLUGIN_INFO_FILE), PluginInfo.class);
        List<ConfigNode> configNodes = ExtUtils.getPluginConfigNodeFromLoader(this.getClass().getClassLoader(), buildInPath);
//        List<ConfigNode> configNodes = MapperHolder.parseJsonToList(ExtUtils.getResourceText(loader, buildInPath + "/" + PluginManager.CONFIG_PROPERTIES_FILE), ConfigNode.class);
        pluginManager.registerPluginResource("sys", pluginInfo, configNodes, buildInPath, loader);
    }

    /**
     * 从classpath中加载插件信息
     */
    public void initPluginFromClassPath(PluginManager pluginManager) throws IOException {

        Enumeration<URL> resources = PluginManager.class.getClassLoader().getResources(PluginManager.PLUGIN_INFO_FILE);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String classpath = PathUtils.getParentPath(url.toString());
            log.info("{}classpath中发现的插件：{}",LOG_PREFIX, classpath);
            if ("file".equals(url.getProtocol()) && !classpath.endsWith(".jar")) {
                // 处理本地文件系统目录作为classpath发现的资源
                UrlResource resource = new UrlResource(classpath);
                String classpathRoot;
                if (OSInfo.isWindows()) {
                    classpathRoot = url.getPath().substring(1);
                } else {
                    classpathRoot = url.getPath();
                }
                pluginManager.register(resource, new DirPathClassLoader(Paths.get(classpathRoot).getParent()));
            } else if (classpath.startsWith("jar:")) {
                // 处理jar包作为classpath发现的资源
                String strUrl = url.toString();
                String jarUrl;
                int jarIndex = strUrl.indexOf("!/");
                if (jarIndex != -1) {
                    jarUrl = strUrl.substring(4, jarIndex);
                    log.info("{}加载classpath中的jar包插件：{}",LOG_PREFIX, jarUrl);
                    pluginManager.register(new UrlResource(jarUrl));
                }

            } else {
                log.warn("{}不支持从该URL中加载插件jar：{}",LOG_PREFIX, url);
            }
        }

        // 注册目录中的jar包插件
        for (URL extUrl : ExtUtils.getExtUrls()) {
            pluginManager.register(new UrlResource(extUrl));
        }

    }
}
