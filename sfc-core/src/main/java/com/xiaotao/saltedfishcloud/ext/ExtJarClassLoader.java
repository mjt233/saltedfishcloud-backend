package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.mina.util.ConcurrentHashSet;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认的基于本地文件系统的拓展Jar包加载器
 */
@Slf4j
public class ExtJarClassLoader extends URLClassLoader implements ExtLoader {
    private final static String LOG_PREFIX = "[拓展]";
    private final Set<URL> loaded = new ConcurrentHashSet<>();

    public ExtJarClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        log.info("{}加载器：{}", LOG_PREFIX, parent.getClass().getName());
    }

    @Override
    public Set<URL> getLoadedExt() {
        return Collections.unmodifiableSet(loaded);
    }

    @Override
    public void addURL(URL url) {
        if (loaded.contains(url)) {
            log.info("{}重复加载拓展{},已忽略", LOG_PREFIX, url);
            return;
        }

        try {
            log.info("{}加载拓展: {}", LOG_PREFIX, url);
            super.addURL(url);
            loaded.add(url);
        } catch (Throwable e) {
            log.error("{}加载失败的拓展：{} 原因：{}", LOG_PREFIX, url, e);
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
            addURL(url);
        }
        log.info("{}======拓展全部加载完成======", LOG_PREFIX);
    }

}
