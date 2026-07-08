package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.ext.*;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.StreamUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.net.URLClassLoader;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

@Slf4j
public class PluginInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private final static String LOG_PREFIX = "[插件初始化]";

    private PluginManager pluginManager;

    public PluginInitializer(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        long begin = System.currentTimeMillis();
        // 加载插件
        pluginManager.init();
        ClassLoader pluginClassLoader = pluginManager.getMergeClassLoader();
        context.setClassLoader(pluginClassLoader);
        Thread.currentThread().setContextClassLoader(pluginClassLoader);
        PluginProperty pluginProperty = PluginProperty.loadFromPropertyResolver(context.getEnvironment());

        // 删除被标记的插件
        try {
            pluginManager.deletePlugin();
        } catch (IOException e) {
            log.error("{}插件删除出错：", LOG_PREFIX, e);
        }
        try {
            // 加载系统核心模块插件信息
            initBuildInPlugin(pluginManager);

            // 执行插件升级替换
            pluginManager.upgrade();

            List<Tuple2<Resource, Supplier<ClassLoader>>> pluginResourceList = new ArrayList<>();

            // 从classpath和ext目录中读取jar包插件
            getPluginFromClassPath(pluginResourceList, context.getEnvironment());

            // 从外部目录中读取非jar包形式的插件
            getPluginFromExtraResource(pluginProperty, pluginResourceList, context.getEnvironment());

            // 开始注册
            startRegister(pluginManager, pluginResourceList);

            // 更新 Jackson 类型工厂的类加载器，使所有 Mapper 能访问插件类
            MapperHolder.setTypeFactoryClassLoader(pluginClassLoader);

            context.addBeanFactoryPostProcessor(beanFactory -> {
                beanFactory.registerSingleton("pluginManager", pluginManager);
            });
            String pluginLists = "[" + String.join(",", pluginManager.getAllPlugin().keySet()) + "]";
            log.info("{}启动时加载的插件清单：{}",LOG_PREFIX, pluginLists);
            log.info("{}插件初始化耗时：{}s",LOG_PREFIX, (System.currentTimeMillis() - begin)/1000d);
        } catch (IOException e) {
            log.error("{}插件信息初始化失败", LOG_PREFIX, e);
            throw new RuntimeException("插件信息初始化失败", e);
        }
        context.addApplicationListener((ApplicationListener<ApplicationStartedEvent>) event -> {
            // 处理独立加载的插件
            List<PluginInfo> alonePluginList = pluginManager.listAllPlugin().stream().filter(e -> e.getLoadType() == PluginLoadType.ALONE).collect(Collectors.toList());
            if (alonePluginList.isEmpty()) {
                log.info("{}没有需要单独加载的插件", LOG_PREFIX);
            } else {
                for (PluginInfo pluginInfo : alonePluginList) {
                    log.info("{}处理单独加载的插件: {}", LOG_PREFIX, pluginInfo.getName());
                    try {
                        pluginManager.loadAlonePlugin(pluginInfo.getName());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // 初始化延迟加载的库
            pluginManager.initDelayLoadLib();

        });
    }



    public void startRegister(PluginManager pluginManager, List<Tuple2<Resource, Supplier<ClassLoader>>> pluginResourceList) throws IOException {

        // 多线程注册
        int size = pluginResourceList.size();
        Semaphore semaphore = new Semaphore(size);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<Resource> errorResourceRef = new AtomicReference<>();

        try { semaphore.acquire(size); } catch (InterruptedException ignore) { }
        for (Tuple2<Resource, Supplier<ClassLoader>> pluginResource : pluginResourceList) {
            PoolUtils.submitInitTask(() -> {
                Resource resource = pluginResource.getT1();
                ClassLoader classLoader = pluginResource.getT2().get();
                try {
                    if (classLoader == null) {
                        pluginManager.register(resource);
                    } else {
                        pluginManager.register(resource, classLoader);
                    }
                } catch (Throwable e) {
                    errorRef.set(e);
                    errorResourceRef.set(pluginResource.getT1());
                } finally {
                    semaphore.release();
                    log.info("{}[OK]插件加载完成: {}", LOG_PREFIX, resource);
                }
            });
        }
        try { semaphore.acquire(size); } catch (InterruptedException ignore) { }

        if (errorRef.get() != null) {
            Throwable throwable = errorRef.get();
            if (throwable instanceof IOException) {
                throw (IOException) throwable;
            } else {
                throw new PluginInfoException(errorResourceRef.get().getFilename() + " " + throwable.getMessage() ,throwable);
            }
        }
    }

    /**
     * 从指定的外部资源中加载插件
     */
    public void getPluginFromExtraResource(PluginProperty pluginProperty,
                                           List<Tuple2<Resource, Supplier<ClassLoader>>> pluginResourceList,
                                           ConfigurableEnvironment environment) throws IOException {
        for (String s : pluginProperty.getExtraResource()) {
            Path path = Paths.get(s).toAbsolutePath();

            // 检测是否为已存在编译结果的项目代码目录，如果是则使用编译输出目录
            Path outputDir = path.resolve("target/classes");
            if (Files.exists(outputDir) && Files.isDirectory(outputDir)) {
                path = outputDir;
            }

            PathResource pathResource = new PathResource(path);
            log.info("{}从额外资源路径加载插件：{}",LOG_PREFIX, path);
            loadPluginSpringConfig(environment, path);

            Path finalPath = path;
            pluginResourceList.add(Tuples.of(pathResource, () -> {
                try {
                    return PluginClassLoaderFactory.createPurePluginClassLoader(finalPath.toUri().toURL());
                } catch (MalformedURLException ignore) { return null; }
            }));
        }
    }

    /**
     * 从开发模式插件目录中补充加载 Spring Boot 标准配置文件。
     * <p>
     * 目录插件的类加载器会在插件初始化阶段才接入 Spring 容器，Spring Boot 默认的
     * ConfigData 加载流程无法自动发现这些目录中的 {@code application.yml}，因此这里
     * 需要手动将插件配置追加到 {@link ConfigurableEnvironment} 中。
     * </p>
     *
     * @param environment Spring 环境
     * @param pluginPath 插件类路径根目录
     * @throws IOException 读取配置文件失败时抛出
     */
    private void loadPluginSpringConfig(ConfigurableEnvironment environment, Path pluginPath) throws IOException {
        MutablePropertySources propertySources = environment.getPropertySources();
        for (String configName : getPluginSpringConfigCandidates(environment)) {
            Path configPath = pluginPath.resolve(configName);
            if (!Files.exists(configPath) || Files.isDirectory(configPath)) {
                continue;
            }
            PropertySourceLoader loader = getPropertySourceLoader(configName);
            if (loader == null) {
                continue;
            }
            UrlResource resource = new UrlResource(configPath.toUri());
            String sourceName = "plugin-config [" + pluginPath.toAbsolutePath() + "] " + configName;
            List<PropertySource<?>> loadedSources = loader.load(sourceName, resource);
            for (int i = loadedSources.size() - 1; i >= 0; --i) {
                propertySources.addLast(loadedSources.get(i));
            }
            log.info("{}加载插件 Spring 配置：{}", LOG_PREFIX, configPath.toAbsolutePath());
        }
    }

    /**
     * 获取插件目录中需要尝试加载的 Spring Boot 标准配置文件名称。
     * <p>
     * 由于配置会被追加到 Environment 末尾，为保证 profile 配置覆盖基础配置，这里按
     * “高优先级在前、低优先级在后”的顺序返回候选文件。
     * </p>
     *
     * @param environment Spring 环境
     * @return 配置文件候选名称列表
     */
    private List<String> getPluginSpringConfigCandidates(ConfigurableEnvironment environment) {
        List<String> result = new ArrayList<>();
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        for (int i = profiles.length - 1; i >= 0; --i) {
            addConfigNames(result, "application-" + profiles[i]);
        }
        addConfigNames(result, "application");
        return result;
    }

    /**
     * 追加指定基础名称对应的 Spring Boot 标准配置文件名。
     *
     * @param container 候选文件名列表
     * @param baseName 配置文件基础名称
     */
    private void addConfigNames(List<String> container, String baseName) {
        container.add(baseName + ".properties");
        container.add(baseName + ".yml");
        container.add(baseName + ".yaml");
    }

    /**
     * 根据文件扩展名获取属性源加载器。
     *
     * @param configName 配置文件名
     * @return 对应的属性源加载器，不支持时返回 {@code null}
     */
    private PropertySourceLoader getPropertySourceLoader(String configName) {
        if (configName.endsWith(".properties")) {
            return new PropertiesPropertySourceLoader();
        }
        if (configName.endsWith(".yml") || configName.endsWith(".yaml")) {
            return new YamlPropertySourceLoader();
        }
        return null;
    }

    /**
     * 初始化内置的插件信息
     */
    public void initBuildInPlugin(PluginManager pluginManager) throws IOException {
        String buildInPath = "build-in-plugin";
        ClassLoader loader = PluginInitializer.class.getClassLoader();
        PluginInfo pluginInfo = ExtUtils.getPluginInfo(this.getClass().getClassLoader(), buildInPath);
        List<ConfigNode> configNodes = ExtUtils.getPluginConfigNodeFromLoader(this.getClass().getClassLoader(), buildInPath);
        pluginManager.registerPluginResource("sys", pluginInfo, configNodes, buildInPath, loader);
    }

    /**
     * 从classpath中加载插件信息
     */
    public void getPluginFromClassPath(List<Tuple2<Resource, Supplier<ClassLoader>>> pluginResourceList,
                                       ConfigurableEnvironment environment) throws IOException {
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
                pluginResourceList.add(Tuples.of(resource, () -> new DirPathClassLoader(Paths.get(classpathRoot).getParent())));
            } else if (classpath.startsWith("jar:nested:")) {
                // todo 感觉没太大必要作为独立的内置jar插件，可以考虑下作为sfc-core的子模块，打包后直接和sfc-core一起就好了，后面再看下怎么处理
                // 原始URL jar:nested:/xxx/sfc-core.jar/!BOOT-INF/lib/sfc-task-core-1.0.0.jar!/plugin-info.json

                // 处理jar包作为classpath发现的资源
                String pluginInfoOriginUrl = url.toString();
                // jar:nested:/xxx/sfc-core.jar/!BOOT-INF/lib/sfc-task-core-1.0.0.jar!/plugin-info.json
                int nestedIdx = pluginInfoOriginUrl.lastIndexOf("!/");
                if (nestedIdx != -1) {
                    // jar:nested:/xxx/sfc-core.jar/!BOOT-INF/lib/sfc-task-core-1.0.0.jar
                    String jarUrl = pluginInfoOriginUrl.substring(0, nestedIdx);
                    log.info("{}加载classpath中的jar包插件：{}",LOG_PREFIX, jarUrl);

                    // 子模块如sfc-task-core作为插件时，是内嵌在sfc-core下的，需要先单独提取出来
                    nestedIdx = jarUrl.lastIndexOf("!");
                    if (nestedIdx != -1) {

                        // BOOT-INF/lib/sfc-task-core-1.0.0.jar
                        String nestedJarName = jarUrl.substring(nestedIdx + 1);
                        Path extraPath = Paths.get("ext")
                                .resolve("nested")
                                .resolve(PathUtils.getLastNode(jarUrl));
                        FileUtils.createParentDirectory(extraPath);
                        log.info("{}提取内嵌的插件jar包: {} => {}", LOG_PREFIX, jarUrl, extraPath);


                        // 提取到ext/nested/下
                        Path jarFilePath = Paths.get(jarUrl.substring(0, nestedIdx - 1).replaceFirst(OSInfo.isWindows() ? "jar:nested:/" : "jar:nested:", ""));
                        try (JarFile parentJarFile = new JarFile(jarFilePath.toFile());
                             InputStream is = parentJarFile.getInputStream(new ZipEntry(nestedJarName));
                             OutputStream os = Files.newOutputStream(extraPath)
                        ) {
                                StreamUtils.copy(is, os);
                        }
                        loadPluginSpringConfig(environment, extraPath.toUri().toURL());
                        pluginResourceList.add(Tuples.of(new UrlResource(extraPath.toUri().toURL()), () -> null));
                    } else {
                        log.warn("{}不支持从该URL中加载插件jar：{}",LOG_PREFIX, url);
                    }
                }

            } else if (classpath.startsWith("jar:")) {
                String pluginInfoOriginUrl = url.toString();
                int nestedIdx = pluginInfoOriginUrl.lastIndexOf("!/");
                if (nestedIdx != -1) {
                    // jar:nested:/xxx/sfc-core.jar/!BOOT-INF/lib/sfc-task-core-1.0.0.jar
                    String jarUrl = pluginInfoOriginUrl.substring(0, nestedIdx);
                    loadPluginSpringConfig(environment, java.net.URI.create(jarUrl).toURL());
                    pluginResourceList.add(Tuples.of(new UrlResource(jarUrl), () -> null));
                } else {
                    log.warn("{}不支持从该URL中加载插件jar：{}",LOG_PREFIX, url);
                }
            } else {
                log.warn("{}不支持从该URL中加载插件jar：{}",LOG_PREFIX, url);
            }
        }

        // 注册目录中的jar包插件
        for (URL extUrl : ExtUtils.getExtUrls()) {
            loadPluginSpringConfig(environment, extUrl);
            pluginResourceList.add(Tuples.of(new UrlResource(extUrl), () -> null));
        }
    }

    /**
     * 从插件 jar 包中补充加载 Spring Boot 标准配置文件。
     * <p>
     * jar 插件不会参与主程序启动初期的 ConfigData 扫描，因此需要先创建一个临时类加载器
     * 主动读取其根路径下的 {@code application.yml} / {@code application-*.yml}。
     * </p>
     *
     * @param environment Spring 环境
     * @param pluginUrl 插件 jar 的 URL
     * @throws IOException 读取配置文件失败时抛出
     */
    private void loadPluginSpringConfig(ConfigurableEnvironment environment, URL pluginUrl) throws IOException {
        try (URLClassLoader loader = PluginClassLoaderFactory.createPurePluginClassLoader(pluginUrl)) {
            for (String configName : getPluginSpringConfigCandidates(environment)) {
                Resource resource = new ClassPathResource(configName, loader);
                if (!resource.exists()) {
                    continue;
                }
                loadPropertySource(environment, resource, configName, pluginUrl.toString());
            }
        }
    }

    /**
     * 加载单个 Spring 配置资源并追加到环境属性源末尾。
     *
     * @param environment Spring 环境
     * @param resource 配置资源
     * @param configName 配置文件名
     * @param sourceId 配置来源标识
     * @throws IOException 读取配置文件失败时抛出
     */
    private void loadPropertySource(ConfigurableEnvironment environment,
                                    Resource resource,
                                    String configName,
                                    String sourceId) throws IOException {
        PropertySourceLoader loader = getPropertySourceLoader(configName);
        if (loader == null) {
            return;
        }
        MutablePropertySources propertySources = environment.getPropertySources();
        String sourceName = "plugin-config [" + sourceId + "] " + configName;
        List<PropertySource<?>> loadedSources = loader.load(sourceName, resource);
        for (int i = loadedSources.size() - 1; i >= 0; --i) {
            propertySources.addLast(loadedSources.get(i));
        }
        log.info("{}加载插件 Spring 配置：{}", LOG_PREFIX, sourceId + "!/" + configName);
    }


}
