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
    private static final String EXTENSION_DIRECTORY = "ext";

    /**
     * 获取拓展目录绝对路径
     */
    public static String getExtensionDirectory() {
        return new File(EXTENSION_DIRECTORY).getAbsolutePath();
    }

    /**
     * 获取拓展模块的URL数组
     * @return 拓展模块的URL
     */
    public static URL[] getExtUrls() {
        final File root = new File(EXTENSION_DIRECTORY);
        if (root.exists()) {
            if (root.isFile()) {
                log.warn("拓展目录路径{}为文件而不是目录！！", root);
                return new URL[0];
            }

            final File[] files = root.listFiles();
            if (files != null) {
                return Arrays.stream(files).filter(e -> e.getName().endsWith(".jar")).map(e -> {
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
