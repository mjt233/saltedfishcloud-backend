package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
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
    private Map<String, JarDependenceInfo> registeredDependencies = new HashMap<>();

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
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                registerDependence(url.toString());
            }
            log.info("{}识别的初始Jar包依赖数量：{}", LOG_PREFIX, registeredDependencies.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void registerDependence(String url) {
        String name = parseJarNameByUrl(url);
        if (name == null) {
            return;
        }
        JarDependenceInfo jarDependenceInfo = getJarDependenceInfoFromName(name);
        JarDependenceInfo exist = registeredDependencies.get(jarDependenceInfo.getName());
        if (exist != null) {
            if (Objects.equals(exist.getVersion(), jarDependenceInfo.getVersion())) {
                log.warn("{}依赖重复：{} 与 {}", LOG_PREFIX, name, jarDependenceInfo);
            } else {
                log.error("{}!!注意!!依赖版本冲突：{} 与 {}",LOG_PREFIX,  exist, jarDependenceInfo);
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
     */
    protected void loadExtraDependencies(List<URL> urls, PluginClassLoader loader) throws IOException {
        // TODO 处理依赖冲突问题
        // TODO 直接从jar内读取依赖而不提取到硬盘
        Files.createDirectories(Paths.get(DEP_EXPLODE_PATH));
        for (URL url : urls) {
            String name = StringUtils.getURLLastName(url.getPath());
            registerDependence(url.toString());
            log.debug("{}加载外部依赖:{}",LOG_PREFIX, name);
            Path tempFile =  Paths.get(DEP_EXPLODE_PATH + "/" + name);
            try(InputStream is = url.openStream(); OutputStream os = Files.newOutputStream(tempFile)) {
                StreamUtils.copy(is, os);
            } catch (IOException e) {
                log.error("加载外部依赖失败：", e);
                continue;
            }
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
            loadExtraDependencies(pluginDependencies, jarMergeClassLoader);
        } catch (Exception e) {
            log.error("获取插件信息失败，请检查插件的plugin-info.json：{}", pluginUrl);
            rawClassLoader.close();
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
