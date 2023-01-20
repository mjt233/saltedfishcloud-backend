package com.xiaotao.saltedfishcloud.ext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.model.Result;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 默认的插件管理器
 */
@Slf4j
//@Component
public class DefaultPluginManager implements PluginManager {

    private final static String LOG_PREFIX = "[插件系统]";
    /**
     * 依赖解包路径
     */
    private final static String DEP_EXPLODE_PATH = "ext/lib";

    private final static Pattern JAR_NAME_PATTERN = Pattern.compile("(?<=/).*?(?=.jar)");
    private final static Pattern JAR_VERSION_PATTERN = Pattern.compile("(?<=-)\\d.*");


    private List<PluginInfo> pluginList = new CopyOnWriteArrayList<>();
    private final Map<String, PluginInfo> pluginMap = new ConcurrentHashMap<>();
    private final Map<String, PluginInfo> pluginMapView = Collections.unmodifiableMap(pluginMap);
    private final Map<String, ClassLoader> pluginClassLoaderMap = new ConcurrentHashMap<>();
    private final Map<String, ClassLoader> pluginRawLoaderMap = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigNode>> pluginConfigNodeGroupMap = new ConcurrentHashMap<>();
    private final Map<String, String> pluginResourceRootMap = new ConcurrentHashMap<>();
    private final PluginClassLoader jarMergeClassLoader;


    /**
     * 已注册的外部依赖
     */
    private final Map<String, JarDependenceInfo> registeredDependencies = new HashMap<>();

    @Getter
    private final ClassLoader masterLoader;

    public DefaultPluginManager() {
        this(DefaultPluginManager.class.getClassLoader());
    }

    public DefaultPluginManager(ClassLoader masterLoader) {
        this.masterLoader = masterLoader;
        this.jarMergeClassLoader = new JarMergePluginClassLoader(masterLoader);
        initPureDependenceJar();
    }

    private void initPureDependenceJar() {
        try {
            Enumeration<URL> resources = masterLoader.getResources("META-INF");
            int successCount = 0;
            int total = 0;
            while (resources.hasMoreElements()) {
                ++total;
                URL url = resources.nextElement();
                try {
                    registerDependence(url.toString());
                    ++successCount;
                } catch (PluginDependenceRepeatException e) {
                    log.warn("{}初始依赖存在重复：{}", LOG_PREFIX, e.getMessage());
                } catch (PluginDependenceConflictException e) {
                    String name = parseJarNameByUrl(url.toString());
                    log.warn("{}初始依赖存在版本冲突：{} 与 {}", LOG_PREFIX, name, e.getMessage());
                }

            }
            log.info("{}识别的初始Jar包依赖数量：{}，成功注册数量：{}", LOG_PREFIX, total, successCount);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册插件的依赖信息
     * @param url   待注册的插件url
     * @return      是否注册成功，存在版本冲突注册失败为false，其他情况均为true
     */
    private synchronized void registerDependence(String url) {
        String name = parseJarNameByUrl(url);
        if (name == null) {
            log.warn("{}插件依赖名称解析失败:{}", LOG_PREFIX, url);
            return;
        }
        JarDependenceInfo jarDependenceInfo = getJarDependenceInfoFromName(name);
        JarDependenceInfo exist = registeredDependencies.get(jarDependenceInfo.getName());
        if (exist != null) {
            if (Objects.equals(exist.getVersion(), jarDependenceInfo.getVersion())) {
                throw new PluginDependenceRepeatException(exist.toString());
            } else {
                throw new PluginDependenceConflictException(exist.toString());
            }
        }
        registeredDependencies.put(jarDependenceInfo.getName(), jarDependenceInfo);
    }

    /**
     * 从Jar包的文件名中识别依赖信息
     * @param name   jar包文件名
     */
    private JarDependenceInfo getJarDependenceInfoFromName(String name) {
        Matcher versionMatcher = JAR_VERSION_PATTERN.matcher(name);
        String version = null;
        while (versionMatcher.find()) {
            version = versionMatcher.group();
        }
        return JarDependenceInfo.builder()
                .version(version)
                .name(version == null ? name : name.substring(0, name.length() - version.length() - 1))
                .build();
    }

    /**
     * 从Jar包的URL资源中识别依赖jar包的文件名
     * @param url   资源URL
     * @return      依赖包名
     */
    private String parseJarNameByUrl(String url) {
        Matcher nameMatcher = JAR_NAME_PATTERN.matcher(url);
        String lastResult = null;
        while (nameMatcher.find()) {
            lastResult = nameMatcher.group();
        }
        if (lastResult == null) {
            log.warn("{}无法识别的依赖资源：{}", LOG_PREFIX, url);
            return null;
        }

        String[] splitGroup = lastResult.split("/");
        return splitGroup[splitGroup.length - 1];
    }

    /**
     * 获取插件的依赖
     * @param rawClassLoader    插件原始加载器
     */
    protected List<URL> getPluginDependencies(ClassLoader rawClassLoader) throws IOException {
        List<URL> res = new ArrayList<>();
        Enumeration<URL> resources = rawClassLoader.getResources("plugin-lib/");
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();

            // 从jar包中获取url
            if ("jar".equals(url.getProtocol())) {
                JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                JarFile jarFile = jarURLConnection.getJarFile();
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("plugin-lib/") && entry.getName().endsWith(".jar") && !entry.isDirectory()) {
                        res.add(rawClassLoader.getResource(entry.getName()));
                    }
                }
            } else if ("file".equals(url.getProtocol())) {
                // 从本地文件中获取
                try {
                    Path path = Paths.get(url.toURI());
                    res.addAll(Files.list(path).map(e -> {
                        try {
                            return e.toUri().toURL();
                        } catch (MalformedURLException ex) {
                            ex.printStackTrace();
                            return null;
                        }
                    }).filter(Objects::nonNull).collect(Collectors.toList()));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        }
        return res;
    }

    /**
     * 加载外部依赖
     * @param urls  外部jar包依赖集合
     * @param loader    加载器
     * @param name 插件名称
     */
    protected void loadExtraDependencies(List<URL> urls, PluginClassLoader loader, String name) throws IOException {
        String pluginUnpackPath = DEP_EXPLODE_PATH + "/" + name;
        Files.createDirectories(Paths.get(pluginUnpackPath));

        List<Result<List<String>, URL>> errorList = new ArrayList<>();
        List<String> conflictList = new ArrayList<>();
        List<URL> passValidUrls = new ArrayList<>();

        // 对每个待加载的url进行 解包 和 校验
        if (urls.size() > 0) {
            log.info("{}验证来自{}的依赖，共{}个", LOG_PREFIX, name, urls.size());
        }
        for (URL url : urls) {
            String depName = StringUtils.getURLLastName(url.getPath());
            Path tempFile =  Paths.get(pluginUnpackPath + "/" + depName);

            // 整包冲突校验
            try {
                registerDependence(url.toString());
            } catch (PluginDependenceConflictException e) {
                log.warn("{}来自插件{}的依赖包版本出现冲突：{} 与 {}", LOG_PREFIX, name, depName, e.getMessage());
                conflictList.add(depName);
                continue;
            } catch (PluginDependenceRepeatException e) {
                log.warn("{}来自插件{}的依赖包版本出现重复，已忽略：{} ", LOG_PREFIX, name, depName);
                continue;
            }

            // 解包依赖
            try(InputStream is = url.openStream(); OutputStream os = Files.newOutputStream(tempFile)) {
                StreamUtils.copy(is, os);
            } catch (IOException e) {
                log.error("{}外部依赖解包失败：",LOG_PREFIX, e);
            }

            // 类加载冲突校验
            Result<List<String>, URL> result = ClassUtils.validUrl(loader, tempFile.toUri().toURL());
            if (!result.isSuccess()) {
                errorList.add(result);
            } else {
                passValidUrls.add(url);
            }
        }

        // 若存在未通过校验的，则展示出来
        if (!errorList.isEmpty()) {
            for (Result<List<String>, URL> result : errorList) {
                String depFullName = StringUtils.getURLLastName(result.getParam().toString());
                log.error("{}外部插件无法加载: {}，存在以下冲突class：{}", LOG_PREFIX, depFullName, result.getData());
            }
            for (String conflictName : conflictList) {
                log.error(conflictName);
            }
            String failJar = Stream.concat(
                        errorList.stream().map(e -> StringUtils.getURLLastName(e.getParam())),
                        conflictList.stream()
                    ).collect(Collectors.joining("\n"));
            log.error("{}以下插件依赖无法被加载：\n{}", LOG_PREFIX, failJar);
            throw new IllegalArgumentException("插件依赖无法加载：\n" + failJar);
        }

        // 通过校验，开始加载依赖
        log.info("{}加载来自{}的依赖：{}", LOG_PREFIX, name, passValidUrls.stream().map(StringUtils::getURLLastName).collect(Collectors.toList()));
        for (URL url : passValidUrls) {
            String depName = StringUtils.getURLLastName(url.getPath());
            Path tempFile =  Paths.get(pluginUnpackPath + "/" + depName);
            loader.loadFromUrl(new PathResource(tempFile).getURL());
        }
    }

    private void validPluginInfo(PluginInfo pluginInfo) {
        if (!StringUtils.hasText(pluginInfo.getName())) {
            throw new IllegalArgumentException("插件缺少name");
        }
        if (!StringUtils.hasText(pluginInfo.getVersion())) {
            throw new IllegalArgumentException("插件缺失有效的version");
        }
        try {
            Version.valueOf(pluginInfo.getVersion());
        } catch (RuntimeException e) {
            log.error("插件version格式错误");
            throw e;
        }

        if (!StringUtils.hasText(pluginInfo.getApiVersion())) {
            throw new IllegalArgumentException("插件缺失有效的apiVersion");
        }
        try {
            Version.valueOf(pluginInfo.getApiVersion());
        } catch (RuntimeException e) {
            log.error("插件apiVersion格式错误");
            throw e;
        }
    }

    @Override
    public void register(Resource pluginResource, ClassLoader classLoader) throws IOException {
        URL pluginUrl = pluginResource.getURL();
        PluginInfo pluginInfo;
        List<ConfigNode> configNodeGroups;

        try {
            // 读取插件基本信息
            log.info("{}加载插件：{}",LOG_PREFIX, StringUtils.getURLLastName(pluginUrl));
            pluginInfo = getPluginInfoFromLoader(classLoader);
            this.validPluginInfo(pluginInfo);

            // 读取配置项
            configNodeGroups = getPluginConfigNodeFromLoader(classLoader);

            // 加载插件所需的外部依赖
            List<URL> pluginDependencies = getPluginDependencies(classLoader);
            if (!pluginDependencies.isEmpty()) {
                loadExtraDependencies(pluginDependencies, jarMergeClassLoader, pluginInfo.getName());
            }

        } catch (JsonProcessingException e) {
            log.error("获取插件信息失败，请检查插件的plugin-info.json：{}", pluginUrl);
            throw e;
        } catch (RuntimeException e) {
            log.error("{}插件 {} 加载失败", LOG_PREFIX, pluginResource.getURL());
            throw e;
        }

        PluginClassLoader loader;
        if (pluginInfo.getLoadType() == PluginLoadType.MERGE) {
            loader = jarMergeClassLoader;
        } else {
            throw new IllegalArgumentException("不支持的拓展加载方式：" + pluginInfo.getLoadType());
        }

        loader.loadFromUrl(pluginUrl);
        registerPluginResource(pluginInfo.getName(), pluginInfo, configNodeGroups, loader);
        pluginRawLoaderMap.put(pluginInfo.getName(), classLoader);
    }


    @Override
    public synchronized void remove(String name) {
        pluginMap.remove(name);
        pluginConfigNodeGroupMap.remove(name);
        pluginClassLoaderMap.remove(name);
        pluginList = pluginList.stream().filter(e -> !Objects.equals(name, e.getName())).collect(Collectors.toList());
        pluginResourceRootMap.remove(name);
    }


    @Override
    public void register(Resource pluginResource) throws IOException {
        ClassLoader loader = PluginClassLoaderFactory.createPurePluginClassLoader(pluginResource.getURL());
        register(pluginResource, loader);
    }

    protected List<ConfigNode> getPluginConfigNodeFromLoader(ClassLoader loader) throws IOException {
        return ExtUtils.getPluginConfigNodeFromLoader(loader, null);
    }

    protected PluginInfo getPluginInfoFromLoader(ClassLoader rawLoader) throws IOException {
        return ExtUtils.getPluginInfo(rawLoader, null);
    }

    @Override
    public void registerPluginResource(String name, PluginInfo pluginInfo, List<ConfigNode> configNodeGroupList, ClassLoader loader) {
        registerPluginResource(name, pluginInfo, configNodeGroupList, "",loader);
    }

    @Override
    public void registerPluginResource(String name, PluginInfo pluginInfo, List<ConfigNode> configNodeGroupList, String resourceRoot, ClassLoader loader) {
        pluginMap.put(pluginInfo.getName(), pluginInfo);
        pluginConfigNodeGroupMap.put(pluginInfo.getName(), configNodeGroupList);
        pluginClassLoaderMap.put(pluginInfo.getName(), loader);
        pluginList.add(pluginInfo);
        pluginResourceRootMap.put(name, resourceRoot);
    }

    @Override
    public Resource getPluginResource(String name, String path) throws PluginNotFoundException {
        ClassLoader loader = pluginRawLoaderMap.getOrDefault(name, pluginClassLoaderMap.get(name));
        if (loader == null) {
            throw new PluginNotFoundException(name);
        }
        String resourceRoot = pluginResourceRootMap.get(name);
        String realPath = StringUtils.appendPath(resourceRoot, path);
        return new ClassPathResource(realPath, loader);
    }

    @Override
    public PluginInfo getPluginInfo(String name) {
        return pluginMapView.get(name);
    }

    @Override
    public Map<String, PluginInfo> getAllPlugin() {
        return pluginMapView;
    }

    @Override
    public List<PluginInfo> listAvailablePlugins() throws IOException {
        List<PluginInfo> pluginInfos = new ArrayList<>();
        Set<String> processedPlugins = new HashSet<>();
        Set<String> deletePlugin = new HashSet<>(listDeletePlugin());

        // 从插件目录中获取插件
        for (URL extUrl : ExtUtils.getExtUrls()) {
            try (URLClassLoader classLoader = PluginClassLoaderFactory.createPurePluginClassLoader(extUrl)) {
                PluginInfo pluginInfo = getPluginInfoFromLoader(classLoader);
                if (pluginInfo.getName() == null) {
                    continue;
                }
                pluginInfo.setIsJar(true);
                processedPlugins.add(pluginInfo.getName());
                pluginInfos.add(pluginInfo);

                // 插件状态判断
                if (deletePlugin.contains(pluginInfo.getName())) {
                    pluginInfo.setStatus(PluginInfo.PLUGIN_WAIT_DELETE);
                } else if (getPluginInfo(pluginInfo.getName()) != null) {
                    pluginInfo.setStatus(PluginInfo.PLUGIN_LOADED);
                } else {
                    pluginInfo.setStatus(PluginInfo.PLUGIN_UNLOADED);
                }
            }
        }

        pluginInfos.addAll(
                listAllPlugin().stream().filter(e -> !processedPlugins.contains(e.getName()))
                .map(e -> {
                    PluginInfo pluginInfo = new PluginInfo();
                    processedPlugins.add(e.getName());
                    BeanUtils.copyProperties(e, pluginInfo);
                    pluginInfo.setStatus(PluginInfo.PLUGIN_LOADED);
                    pluginInfo.setIsJar(false);
                    return pluginInfo;
                }).collect(Collectors.toList())
        );
        return pluginInfos;
    }



    /**
     * 获取待删除的插件名称
     */
    @Override
    public List<String> listDeletePlugin() {
        Path deleteRecord = ExtUtils.getExtDir().resolve("delete.json");
        if (!Files.exists(deleteRecord)) {
            return Collections.emptyList();
        }
        try (InputStream inputStream = Files.newInputStream(deleteRecord)) {
            return MapperHolder.parseJsonToList(StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8), String.class);
        } catch (IOException e) {
            log.error("{}获取待删除插件出错", LOG_PREFIX,e);
            return Collections.emptyList();
        }
    }

    @Override
    public void deletePlugin() throws IOException {
        // 获取被标记删除的插件
        Path deleteRecordPath = ExtUtils.getExtDir().resolve("delete.json");
        if (!Files.exists(deleteRecordPath) || Files.isDirectory(deleteRecordPath)) {
            return;
        }
        List<String> names;
        try (InputStream stream = Files.newInputStream(deleteRecordPath)) {
            names = MapperHolder.parseJsonToList(StreamUtils.copyToString(stream, StandardCharsets.UTF_8), String.class);
        }

        // 获取待删除的插件信息
        String loaded = names.stream().filter(e -> getPluginInfo(e) != null).collect(Collectors.joining(","));
        if(StringUtils.hasText(loaded)) {
            throw new IllegalArgumentException("已加载的插件无法删除:" + loaded);
        }
        Map<String, PluginInfo> pluginMap = listAvailablePlugins().stream().collect(Collectors.toMap(PluginInfo::getName, Function.identity()));

        for (String name : names) {
            try {
                PluginInfo pluginInfo = pluginMap.get(name);
                if (pluginInfo == null) {
                    log.warn("{}插件 [{}]无法识别，跳过删除", LOG_PREFIX, name);
                    continue;
                }

                // 删除插件本体
                Path path = Paths.get(new URL(pluginInfo.getUrl()).getPath().replaceAll("^/+", ""));
                log.info("{}删除插件: [{}]", LOG_PREFIX, path);
                Files.deleteIfExists(path);

                // 删除附带的依赖
                Path dependencePath = Paths.get(StringUtils.appendPath(DEP_EXPLODE_PATH, name));
                if (Files.exists(dependencePath) && Files.isDirectory(dependencePath)) {
                    FileUtils.delete(dependencePath);
                }
            } catch (Exception e) {
                log.error("{}删除插件失败：", LOG_PREFIX, e);
            }
        }

        // 清空删除标记文件
        Files.deleteIfExists(deleteRecordPath);
    }

    /**
     * 标记插件为待删除
     */
    @Override
    public void markPluginDelete(String name) throws IOException {
        Map<String, PluginInfo> pluginMap = listAvailablePlugins().stream().collect(Collectors.toMap(PluginInfo::getName, Function.identity()));
        PluginInfo plugin = pluginMap.get(name);
        if (plugin == null) {
            throw new IllegalArgumentException("该插件不存在");
        } else if (plugin.getIsJar() == null || !plugin.getIsJar()) {
            throw new IllegalArgumentException("该插件不是通过jar包加载，无法删除");
        }

        Path deleteRecord = ExtUtils.getExtDir().resolve("delete.json");
        Set<String> deleteNameList = new HashSet<>(listDeletePlugin());
        deleteNameList.add(name);
        String json = MapperHolder.toJson(deleteNameList);
        try (OutputStream outputStream = Files.newOutputStream(deleteRecord)) {
            StreamUtils.copy(json, StandardCharsets.UTF_8, outputStream);
        }
    }

    @Override
    public List<PluginInfo> listAllPlugin() {
        return pluginList;
    }

    @Override
    public ClassLoader getPluginClassLoader(String name) throws PluginNotFoundException {
        if (!pluginClassLoaderMap.containsKey(name)){
            throw new PluginNotFoundException(name);
        }

        return pluginClassLoaderMap.get(name);
    }

    @Override
    public ClassLoader getJarMergeClassLoader() {
        return jarMergeClassLoader;
    }

    @Override
    public List<ConfigNode> getPluginConfigNodeGroup(String name) {
        return pluginConfigNodeGroupMap.get(name);
    }

//    @Override
//    public synchronized void loadPlugin(String name) throws IOException {
//        if(getPluginInfo(name) != null) {
//            throw new IllegalArgumentException("插件已加载");
//        }
//        Path jarPath = Paths.get(ExtUtils.getExtDir().getAbsolutePath()).resolve(name + ".jar");
//        if (!Files.exists(jarPath) || Files.isDirectory(jarPath)) {
//            throw new FileNotFoundException("插件 【" + name + "】 不存在");
//        }
//
//        try (URLClassLoader classLoader = PluginClassLoaderFactory.createPurePluginClassLoader(jarPath.toUri().toURL())) {
//            PluginInfo pluginInfo = getPluginInfoFromLoader(classLoader);
//            jarMergeClassLoader.loadFromUrl(jarPath.toUri().toURL());
//            List<Class<?>> classList = getAutoConfigurationClass(classLoader);
//            for (Class<?> clazz : classList) {
//                SpringContextUtils.registerBean(clazz);
//            }
//        } catch (IOException e) {
//            throw new IOException("获取插件信息失败，请检查插件的plugin-info.json：{}", e);
//        }
//    }
//
//    private List<Class<?>> getAutoConfigurationClass(ClassLoader classLoader) throws IOException {
//        Properties properties = new Properties();
//        try (InputStream stream = classLoader.getResourceAsStream("META-INF/spring.factories")) {
//            if (stream == null) {
//                throw new IllegalArgumentException("未在META-INF/spring.factories中指定自动配置类");
//            }
//            properties.load(stream);
//        }
//        String autoConfigClass = properties.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
//        if (!StringUtils.hasText(autoConfigClass)) {
//            throw new IllegalArgumentException("未在META-INF/spring.factories中指定自动配置类");
//        }
//        return Arrays.stream(autoConfigClass.split(","))
//                .map(clazz -> {
//                    try {
//                        return classLoader.loadClass(clazz);
//                    } catch (ClassNotFoundException e) {
//                        throw new IllegalArgumentException("无效的自动配置类：" + clazz, e);
//                    }
//                })
//                .collect(Collectors.toList());
//    }
}
