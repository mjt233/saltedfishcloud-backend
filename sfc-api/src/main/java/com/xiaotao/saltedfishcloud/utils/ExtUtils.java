package com.xiaotao.saltedfishcloud.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
public class ExtUtils {
    public static ClassLoader loadExtJar(ClassLoader parent) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final File root = new File("./lib");

            final Class<?> appLoaderClass = Class.forName("sun.misc.Launcher$AppClassLoader");
            if (appLoaderClass.isAssignableFrom(parent.getClass())) {
                final Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);

                log.info("[拓展]加载拓展路径：{}", root.getAbsolutePath());
                final URL[] urls = ExtUtils.getExtUrls();
                for (URL url : urls) {
                    log.info("[拓展]加载拓展：{}", url);
                    addURL.invoke(parent, url);
                }
            }
            return new URLClassLoader(ExtUtils.getExtUrls());
    }

    /**
     * 获取拓展模块的URL数组
     * @return 拓展模块的URL
     */
    public static URL[] getExtUrls() {
        final File root = new File("./lib");
        if (root.exists()) {
            if (root.isFile()) {
                log.warn("[拓展]拓展资源路径{}不是目录", root.getAbsolutePath());
                return new URL[0];
            }

            final File[] files = root.listFiles();
            if (files != null) {
                return Arrays.stream(files).map(e -> {
                    try {
                        return e.toURI().toURL();
                    } catch (MalformedURLException ex) {
                        ex.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull).toArray(URL[]::new);
            } else {
                return new URL[0];
            }
        } else {
            return new URL[0];
        }
    }
}
