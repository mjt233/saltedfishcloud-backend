package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.mina.util.ConcurrentHashSet;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * 默认的基于本地文件系统的拓展Jar包加载器
 */
@Slf4j
public class JarMergePluginClassLoader extends PluginClassLoader {
    private final static String LOG_PREFIX = "[JarMerge]";
    private final Set<URL> loaded = new ConcurrentHashSet<>();

    public JarMergePluginClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        log.info("{}加载器：{}", LOG_PREFIX, parent.getClass().getName());
    }

    @Override
    public Set<URL> getLoadedExt() {
        return Collections.unmodifiableSet(loaded);
    }

    @Override
    public void loadFromUrl(URL url) {
        if (loaded.contains(url)) {
            log.info("{}重复加载拓展{},已忽略", LOG_PREFIX, url);
            return;
        }

        this.addURL(url);
        loaded.add(url);
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
