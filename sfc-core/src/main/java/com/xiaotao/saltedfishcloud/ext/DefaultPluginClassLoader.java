package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认的基于本地文件系统的拓展Jar包加载器
 */
@Slf4j
public class DefaultPluginClassLoader extends PluginClassLoader {
    private final static String LOG_PREFIX = "[JarMerge]";
    private final Set<URL> loaded = new HashSet<>();

    public DefaultPluginClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        log.info("{}加载器：{}", LOG_PREFIX, parent.getClass().getName());
    }

    @Override
    public Set<URL> getLoadedExt() {
        return Collections.unmodifiableSet(loaded);
    }

    @Override
    public void loadFromUrl(URL url) {
        synchronized (loaded) {
            if (loaded.contains(url)) {
                log.info("{}重复加载拓展{},已忽略", LOG_PREFIX, url);
                return;
            }
        }

        this.addURL(url);
        synchronized (loaded) {
            loaded.add(url);
        }

    }


    @Override
    public Set<URL> getAvailableExtUrl() {
        return Arrays.stream(ExtUtils.getExtUrls()).collect(Collectors.toSet());
    }

    @Override
    public void loadAll() {
        log.info("{}======读取拓展并加载======", LOG_PREFIX);
        for (URL url : getAvailableExtUrl()) {
            loadFromUrl(url);
        }
        log.info("{}======拓展全部加载完成======", LOG_PREFIX);
    }

    /**
     * 获取资源时，对于 SPI 服务配置文件，检查其服务接口在父加载器中是否可见。
     * 若不可见则跳过父加载器上的服务文件，避免 ServiceLoader 加载到由父加载器定义
     * 的实现类，但其接口仅对当前插件加载器可见，导致 NoClassDefFoundError。
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (name.startsWith("META-INF/services/")) {
            String serviceClassName = name.substring("META-INF/services/".length());
            try {
                Class.forName(serviceClassName, false, getParent());
            } catch (ClassNotFoundException e) {
                log.debug("{}SPI服务接口{}在父加载器中不可见，跳过父加载器上的服务文件", LOG_PREFIX, serviceClassName);
                return findResources(name);
            }
        }
        return super.getResources(name);
    }

}
