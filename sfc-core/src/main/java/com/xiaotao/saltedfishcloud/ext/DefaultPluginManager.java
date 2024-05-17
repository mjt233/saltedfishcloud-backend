package com.xiaotao.saltedfishcloud.ext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sfc.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.model.Result;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import java.util.zip.ZipEntry;

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

    private final static String DELETE_JSON = "delete.json";

    private final static String UPGRADE_JSON = "upgrade.json";

    private final static String UPGRADE_SUFFIX = ".upgrade";

    private final static Pattern JAR_NAME_PATTERN = Pattern.compile("(?<=/).*?(?=.jar)");
    private final static Pattern JAR_VERSION_PATTERN = Pattern.compile("(?<=-)\\d.*");


    private List<PluginInfo> pluginList = new CopyOnWriteArrayList<>();
    private final Map<String, PluginInfo> pluginMap = new ConcurrentHashMap<>();
    private final Map<String, PluginInfo> pluginMapView = Collections.unmodifiableMap(pluginMap);
    private final Map<String, ClassLoader> pluginClassLoaderMap = new ConcurrentHashMap<>();
    private final Map<String, ClassLoader> pluginRawLoaderMap = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigNode>> pluginConfigNodeGroupMap = new ConcurrentHashMap<>();
    private final Map<String, String> pluginResourceRootMap = new ConcurrentHashMap<>();
    private final PluginClassLoader mergeClassLoader;

    private final Map<String, URL> pluginOriginUrl = new ConcurrentHashMap<>();

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
        this.mergeClassLoader = new DefaultPluginClassLoader(masterLoader);
    }

    /**
     * 初始化未加载插件时，当前引用的依赖信息
     */
    @Override
    public void init() {
        try {
            Enumeration<URL> resources = masterLoader.getResources("META-INF");
            int successCount = 0;
            int total = 0;
            while (resources.hasMoreElements()) {
                ++total;
                URL url = resources.nextElement();
                try {
                    checkLibDependence(url.toString(), true);
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

    @Override
    public void loadAlonePlugin(String name) throws IOException {
        // 功能未实现
        PluginInfo pluginInfo = Objects.requireNonNull(pluginMap.get(name), "插件" + name + "不存在");
        if (pluginInfo.getLoadType() != PluginLoadType.ALONE) {
            throw new IllegalArgumentException(name + "不是独立加载依赖的插件");
        }
//        ClassLoader classLoader = pluginRawLoaderMap.get(name);
//        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
//        Resource[] resources = resolver.getResources("classpath*:**.class");
//        for (Resource resource : resources) {
//            System.out.println(resource.getURL());
//        }
    }

    @Override
    public void initDelayLoadLib() {
        log.info("{}开始进行延迟加载第三方库", LOG_PREFIX);
        listAllPlugin().stream().filter(pluginInfo -> pluginInfo.getDelayLoadLib() != null && pluginInfo.getDelayLoadLib().size() > 0)
                .forEach(pluginInfo -> {
                    ClassLoader loader = pluginClassLoaderMap.get(pluginInfo.getName());
                    if (!(loader instanceof PluginClassLoader)) {
                        throw new IllegalArgumentException("插件" + pluginInfo.getName() + "的ClassLoader不是PluginClassLoader，无法使用延迟加载。插件当前的ClassLoader为" + loader.getClass());
                    }

                    Set<String> delayLoadLibSet = new HashSet<>(pluginInfo.getDelayLoadLib());
                    try {
                        List<URL> delayLoadLibUrlList = getPluginDependencies(pluginRawLoaderMap.get(pluginInfo.getName()))
                                .stream()
                                .filter(url -> delayLoadLibSet.contains(PathUtils.getLastNode(url.toString())))
                                .collect(Collectors.toList());
                        loadExtraDependencies(delayLoadLibUrlList,  (PluginClassLoader) loader,pluginInfo);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * 检查插件的外部库依赖信息是否有冲突
     * @param url   待检查的插件依赖包的url
     * @param isRegister 是否记录到现有插件外部依赖库记录中
     */
    private synchronized void checkLibDependence(String url, boolean isRegister) {
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
        if (isRegister) {
            registeredDependencies.put(jarDependenceInfo.getName(), jarDependenceInfo);
        }
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
     * @param pluginInfo 插件信息
     */
    protected void loadExtraDependencies(List<URL> urls, PluginClassLoader loader, PluginInfo pluginInfo) throws IOException {
        String pluginUnpackPath = DEP_EXPLODE_PATH + "/" + pluginInfo.getName();
        Files.createDirectories(Paths.get(pluginUnpackPath));

        List<Result<List<String>, URL>> errorList = new ArrayList<>();
        List<String> conflictList = new ArrayList<>();
        List<URL> passValidUrls = new ArrayList<>();

        // 对每个待加载的url进行 解包 和 校验
        if (urls.size() > 0) {
            log.info("{}验证来自{}的依赖，共{}个", LOG_PREFIX, pluginInfo.getName(), urls.size());
        }
        for (URL url : urls) {
            String depName = StringUtils.getURLLastName(url.getPath());
            Path tempFile =  Paths.get(pluginUnpackPath + "/" + depName);

            // 整包冲突校验
            try {
                checkLibDependence(url.toString(), pluginInfo.getLoadType() == PluginLoadType.MERGE);
            } catch (PluginDependenceConflictException e) {
                log.warn("{}来自插件{}的依赖包版本出现冲突：{} 与 {}", LOG_PREFIX, pluginInfo.getName(), depName, e.getMessage());
                conflictList.add(depName);
                continue;
            } catch (PluginDependenceRepeatException e) {
                log.warn("{}来自插件{}的依赖包版本出现重复，已忽略：{} ", LOG_PREFIX, pluginInfo.getName(), depName);
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
        log.info("{}加载来自{}的依赖：{}", LOG_PREFIX, pluginInfo.getName(), passValidUrls.stream().map(StringUtils::getURLLastName).collect(Collectors.toList()));
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


        PluginClassLoader loader;
        try {
            // 读取插件基本信息
            log.info("{}开始加载插件：{}",LOG_PREFIX, pluginUrl);
            pluginInfo = getPluginInfoFromLoader(classLoader);
            this.validPluginInfo(pluginInfo);

            if (pluginInfo.getLoadType() == PluginLoadType.MERGE) {
                loader = mergeClassLoader;
            } else {
                loader = new DefaultPluginClassLoader(masterLoader);
            }

            // 读取配置项
            configNodeGroups = getPluginConfigNodeFromLoader(classLoader);

            // 加载插件所需的外部依赖
            Set<String> delayLoadLib = new HashSet<>(Optional.ofNullable(pluginInfo.getDelayLoadLib()).orElse(Collections.emptyList()));
            List<URL> pluginDependencies = getPluginDependencies(classLoader)
                    .stream()
                    .filter(url -> !delayLoadLib.contains(PathUtils.getLastNode(url.toString())))
                    .collect(Collectors.toList());
            if (!pluginDependencies.isEmpty()) {
                loadExtraDependencies(pluginDependencies, loader, pluginInfo);
            }

        } catch (JsonProcessingException e) {
            log.error("获取插件信息失败，请检查插件的plugin-info.json：{}", pluginUrl);
            throw e;
        } catch (RuntimeException e) {
            log.error("{}插件 {} 加载失败", LOG_PREFIX, pluginResource.getURL());
            throw e;
        }

        loader.loadFromUrl(pluginUrl);
        registerPluginResource(pluginInfo.getName(), pluginInfo, configNodeGroups, loader);
        pluginRawLoaderMap.put(pluginInfo.getName(), classLoader);
        pluginOriginUrl.put(pluginInfo.getName(), pluginUrl);
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
        URL url = extraNestingJarUrl(pluginResource.getURL());
        ClassLoader loader = PluginClassLoaderFactory.createPurePluginClassLoader(url);
        register(pluginResource, loader);
    }

    /**
     * 提取嵌套jar的url
     */
    private URL extraNestingJarUrl(URL url) throws IOException {
        // 检测是否有嵌套jar
        if (!url.getProtocol().equals("jar")) {
            return url;
        }
        int i = url.toString().indexOf("!");
        if (i != -1 && i == url.toString().length() - 1) {
            return url;
        }

        // 拆分最外层jar 和 嵌套jar的内部路径
        URL main = new URL(url.toString().substring(0, i) + "!/");
        String substring = url.toString().substring(i + 2);

        // 读取最外层jar
        URLConnection connection = main.openConnection();
        if (!(connection instanceof JarURLConnection)) {
            return url;
        }

        // 从最外层jar提取嵌套jar到硬盘中
        try (JarFile jar = ((JarURLConnection) connection).getJarFile()) {
            ZipEntry entry = jar.getEntry(substring);
            if (entry != null) {
                String fileName = PathUtils.getLastNode(entry.getName());
                Path extraPath = Paths.get(DEP_EXPLODE_PATH).resolve("extra").resolve(fileName);
                FileUtils.createParentDirectory(extraPath);
                try (InputStream is = jar.getInputStream(entry);
                    OutputStream os = Files.newOutputStream(extraPath)) {
                    StreamUtils.copy(is, os);
                }
                // 返回提取出来的jar url
                return extraPath.toUri().toURL();
            }
        }
        return url;
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
                PluginInfo pluginInfo;
                try {
                    pluginInfo = getPluginInfoFromLoader(classLoader);
                } catch (Exception e) {
                    throw new PluginInfoException("无法读取插件信息:" + extUrl + " 异常:", e);
                }
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

        // 追加插件目录中没有但已加载的插件（系统插件、开发模式加载的插件）
        pluginInfos.addAll(
                listAllPlugin().stream().filter(e -> !processedPlugins.contains(e.getName()))
                .map(e -> {
                    PluginInfo pluginInfo = new PluginInfo();
                    processedPlugins.add(e.getName());
                    BeanUtils.copyProperties(e, pluginInfo);
                    pluginInfo.setStatus(PluginInfo.PLUGIN_LOADED);
                    pluginInfo.setIsJar(false);
                    // url取原始url
                    URL url = pluginOriginUrl.get(pluginInfo.getName());
                    if (url != null) {
                        pluginInfo.setUrl(url.toString());
                    }
                    return pluginInfo;
                }).collect(Collectors.toList())
        );

        // 识别和整合插件升级信息
        Map<String, String> upgradeRecord = getUpgradeRecord();
        for (PluginInfo pluginInfo : pluginInfos) {
            String upgradeName = upgradeRecord.get(pluginInfo.getName());
            if (upgradeName == null) {
                continue;
            }

            try {
                URL url = ExtUtils.getExtDir().resolve(upgradeName + UPGRADE_SUFFIX).toUri().toURL();
                PluginInfo upgradeInfo = parsePlugin(url);
                pluginInfo.setUpgradeVersion(upgradeInfo.getVersion());
            } catch (Exception e) {
                log.error("{}识别插件升级信息出错：", LOG_PREFIX, e);
            }

        }
        return pluginInfos;
    }



    /**
     * 获取待删除的插件名称
     */
    @Override
    public List<String> listDeletePlugin() {
        Path deleteRecord = ExtUtils.getExtDir().resolve(DELETE_JSON);
        if (!Files.exists(deleteRecord)) {
            return new ArrayList<>();
        }
        try (InputStream inputStream = Files.newInputStream(deleteRecord)) {
            return MapperHolder.parseJsonToList(StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8), String.class);
        } catch (IOException e) {
            log.error("{}获取待删除插件出错", LOG_PREFIX,e);
            return new ArrayList<>();
        }
    }

    @Override
    public void deletePlugin() throws IOException {
        // 获取被标记删除的插件
        Path deleteRecordPath = ExtUtils.getExtDir().resolve(DELETE_JSON);
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
                Path path = getPluginPath(pluginInfo);
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

    private Path getPluginPath(PluginInfo pluginInfo) throws MalformedURLException {
        if (OSInfo.isWindows()) {
            return Paths.get(new URL(pluginInfo.getUrl()).getPath().replaceAll("^/+", ""));
        } else {
            return Paths.get(new URL(pluginInfo.getUrl()).getPath());
        }
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

        // 如果插件还没被加载，直接删掉文件完事
        if (PluginInfo.PLUGIN_UNLOADED == Optional.ofNullable(plugin.getStatus()).orElse(PluginInfo.PLUGIN_LOADED)) {
            Path existPath = getPluginPath(plugin);
            Files.deleteIfExists(existPath);
            return;
        }

        // 如果文件处于待更新状态，则取消更新标记
        Map<String, String> upgradeRecord = getUpgradeRecord();
        String upgradeName = upgradeRecord.get(name);
        if (upgradeName != null) {
            upgradeRecord.remove(name);
            saveUpgradeRecord(upgradeRecord);
            Files.deleteIfExists(ExtUtils.getExtDir().resolve(upgradeName));
        }


        Set<String> deleteNameList = new HashSet<>(listDeletePlugin());
        deleteNameList.add(name);
        saveDeleteList(deleteNameList);
    }

    /**
     * 保存待删除的插件列表
     */
    private synchronized void saveDeleteList(Collection<String> deleteNameList) throws IOException {
        String json = MapperHolder.toJson(deleteNameList);
        Path deleteRecord = ExtUtils.getExtDir().resolve(DELETE_JSON);
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
    public ClassLoader getMergeClassLoader() {
        return mergeClassLoader;
    }

    @Override
    public List<ConfigNode> getPluginConfigNodeGroup(String name) {
        return pluginConfigNodeGroupMap.get(name);
    }

    @Override
    public void installPlugin(Resource resource) throws IOException {
        if (!StringUtils.hasText(resource.getFilename())) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        long id = IdUtil.getId();
        Path path = ExtUtils.getExtDir();
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }

        // 暂存并校验插件
        Path temp = path.resolve(id + ".temp");
        try (InputStream is = resource.getInputStream(); OutputStream os = Files.newOutputStream(temp)) {
            StreamUtils.copy(is, os);
        }
        PluginInfo pluginInfo = parsePlugin(temp.toUri().toURL());

        // 如果已经被标记了删除，则移除删除标记
        List<String> deleteList = listDeletePlugin();
        boolean markDelete = deleteList.stream().anyMatch(e -> pluginInfo.getName().equals(e));
        if (markDelete) {
            saveDeleteList(new HashSet<>(deleteList){{remove(pluginInfo.getName());}});
        }

        // 如果已存在同名插件，则标记为本次为升级操作，在下次启动时执行升级程序。
        // 否则直接复制文件到插件目录
        boolean exist = listAvailablePlugins().stream().anyMatch(e -> Objects.equals(pluginInfo.getName(), e.getName()));
        if (exist) {
            Path upgradeName = ExtUtils.getExtDir().resolve(resource.getFilename() + UPGRADE_SUFFIX);
            Files.move(temp, upgradeName, StandardCopyOption.REPLACE_EXISTING);
            Map<String, String> upgradeRecord = getUpgradeRecord();
            upgradeRecord.put(pluginInfo.getName(), resource.getFilename());
            saveUpgradeRecord(upgradeRecord);
        } else {
            Files.move(temp, ExtUtils.getExtDir().resolve(resource.getFilename()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 获取待升级的插件记录
     * @return  key - 插件名称，value - 待升级文件名
     */
    private Map<String, String> getUpgradeRecord() {
        Path path = ExtUtils.getExtDir().resolve(UPGRADE_JSON);
        if (!Files.exists(path)) {
            return new HashMap<>();
        } else {
            try (InputStream is = Files.newInputStream(path)) {
                String json = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                return MapperHolder.parseJsonToMap(json, String.class, String.class);
            } catch (IOException e) {
                log.error("{}读取升级记录失败：", LOG_PREFIX, e);
                return new HashMap<>();
            }
        }
    }

    /**
     * 保存升级记录
     * @param record    升级记录map key - 插件名称，value - 待升级文件名
     */
    private synchronized void saveUpgradeRecord(Map<String, String> record) throws IOException {
        Path extDir = ExtUtils.getExtDir();
        if (!Files.exists(extDir)) {
            Files.createDirectories(extDir);
        }
        Path path = extDir.resolve(UPGRADE_JSON);
        String json = MapperHolder.toJson(record);
        try (OutputStream os = Files.newOutputStream(path)) {
            StreamUtils.copy(json, StandardCharsets.UTF_8, os);
        }
    }

    @Override
    public void upgrade() throws IOException {
        Map<String, String> upgradeRecord = getUpgradeRecord();
        Map<String, PluginInfo> pluginMap = listAvailablePlugins().stream().collect(Collectors.toMap(PluginInfo::getName, Function.identity()));
        upgradeRecord.forEach((name, fileName) -> {
            try {
                PluginInfo pluginInfo = pluginMap.get(name);
                if (pluginInfo == null) {
                    log.warn("{}插件升级未找到原插件", LOG_PREFIX);
                } else {
                    Path pluginPath = getPluginPath(pluginInfo);
                    Files.deleteIfExists(pluginPath);
                    log.info("{}插件 【{}】升级，删除原插件：{}", LOG_PREFIX, name, pluginPath.getFileName());
                }
                Path originPath = ExtUtils.getExtDir().resolve(fileName + UPGRADE_SUFFIX);
                Path targetPath = ExtUtils.getExtDir().resolve(fileName);
                Files.move(originPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("{}插件升级: 【{}】", LOG_PREFIX, name);

            } catch (IOException e) {
                log.error("{}插件 【{}】升级出错：", LOG_PREFIX, name, e);
            }
        });
        Files.deleteIfExists(ExtUtils.getExtDir().resolve(UPGRADE_JSON));
    }

    @Override
    public PluginInfo parsePlugin(URL url) throws IOException {
        try (URLClassLoader classLoader = PluginClassLoaderFactory.createPurePluginClassLoader(url)) {
            PluginInfo pluginInfo;
            try {
                pluginInfo = getPluginInfoFromLoader(classLoader);
            } catch (IOException e) {
                throw new IllegalArgumentException("插件信息获取失败，请检查插件文件格式是否正确或该插件是否未配置信息");
            }

            validPluginInfo(pluginInfo);
            if (url.getPath().endsWith(".jar")) {
                pluginInfo.setIsJar(true);
            }
            return pluginInfo;
        }
    }

    @Override
    public void close() throws IOException {
        mergeClassLoader.close();
        for (Map.Entry<String, ClassLoader> entry : pluginRawLoaderMap.entrySet()) {
            String plugin = entry.getKey();
            ClassLoader classLoader = entry.getValue();
            if (classLoader instanceof Closeable) {
                log.info("{}关闭插件:{}", LOG_PREFIX, plugin);
                ((Closeable) classLoader).close();
            }
        }
        pluginClassLoaderMap.values().stream()
                .filter(c -> c != mergeClassLoader)
                .forEach(c -> {
                    if (c instanceof Closeable) {
                        try {
                            ((Closeable) c).close();
                        } catch (IOException ignore) { }
                    }
                });
    }

    @Override
    public void refreshPluginConfig() {
        pluginRawLoaderMap.forEach((pluginName, loader) -> {
            try {
                log.info("{}刷新插件{}的配置信息", LOG_PREFIX, pluginName);
                String prefix = pluginResourceRootMap.getOrDefault(pluginName, "");
                List<ConfigNode> nodes = ExtUtils.getPluginConfigNodeFromLoader(loader, prefix);
                pluginConfigNodeGroupMap.put(pluginName, nodes);
            } catch (IOException e) {
                log.error("{}更新插件配置项失败", LOG_PREFIX);
                throw new JsonException(500, e.getMessage());
            }
        });
    }
}
