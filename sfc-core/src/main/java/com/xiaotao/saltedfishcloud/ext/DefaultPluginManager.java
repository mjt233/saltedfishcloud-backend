package com.xiaotao.saltedfishcloud.ext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.model.Result;
import com.xiaotao.saltedfishcloud.utils.ClassUtils;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
public class DefaultPluginManager implements PluginManager {

    private final static String LOG_PREFIX = "[插件系统]";
    /**
     * 依赖解包路径
     */
    private final static String DEP_EXPLODE_PATH = "ext/lib";
    private final List<PluginInfo> pluginList = new CopyOnWriteArrayList<>();
    private final Map<String, PluginInfo> pluginMap = new ConcurrentHashMap<>();
    private final Map<String, PluginInfo> pluginMapView = Collections.unmodifiableMap(pluginMap);
    private final Map<String, ClassLoader> pluginClassLoaderMap = new ConcurrentHashMap<>();
    private final Map<String, ClassLoader> pluginRawLoaderMap = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigNode>> pluginConfigNodeGroupMap = new ConcurrentHashMap<>();
    private final Map<String, String> pluginResourceRootMap = new ConcurrentHashMap<>();
    private final PluginClassLoader jarMergeClassLoader;
    private final Pattern JAR_NAME_PATTERN = Pattern.compile("(?<=/).*?(?=.jar)");
    private final Pattern JAR_VERSION_PATTERN = Pattern.compile("(?<=-)\\d.*");

    /**
     * 已注册的外部依赖
     */
    private final Map<String, JarDependenceInfo> registeredDependencies = new HashMap<>();

    @Getter
    private final ClassLoader masterLoader;


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
            if (!url.getProtocol().equals("jar")) {
                continue;
            }
            JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
            JarFile jarFile = jarURLConnection.getJarFile();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith("plugin-lib/") && entry.getName().endsWith(".jar") && !entry.isDirectory()) {
                    res.add(rawClassLoader.getResource(entry.getName()));
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
        // TODO 直接从jar内读取依赖而不提取到硬盘
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

    @Override
    public void register(Resource pluginResource) throws IOException {
        URL pluginUrl = pluginResource.getURL();
        URLClassLoader rawClassLoader = new URLClassLoader(new URL[]{pluginUrl}, null);
        PluginInfo pluginInfo;
        List<ConfigNode> configNodeGroups;

        try {
            log.info("{}加载插件：{}",LOG_PREFIX, StringUtils.getURLLastName(pluginUrl));
            pluginInfo  = getPluginInfoFromLoader(rawClassLoader);
            configNodeGroups = getPluginConfigNodeFromLoader(rawClassLoader);
            List<URL> pluginDependencies = getPluginDependencies(rawClassLoader);
            loadExtraDependencies(pluginDependencies, jarMergeClassLoader, pluginInfo.getName());
        } catch (JsonProcessingException e) {
            log.error("获取插件信息失败，请检查插件的plugin-info.json：{}", pluginUrl);
            rawClassLoader.close();
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
        pluginRawLoaderMap.put(pluginInfo.getName(), rawClassLoader);

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
}
