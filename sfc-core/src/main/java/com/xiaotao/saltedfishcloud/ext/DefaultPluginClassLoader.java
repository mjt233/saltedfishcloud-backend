package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
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

}
