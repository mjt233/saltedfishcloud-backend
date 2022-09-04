package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.Result;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassUtils {

    public static List<URL> getAllResources(ClassLoader loader, String prefix) throws IOException {
        List<URL> res = new ArrayList<>();
        getAllResources(loader, prefix, res);
        return res;
    }

    private static void getAllResources(ClassLoader loader, String prefix, List<URL> res) throws IOException {
        Enumeration<URL> resources = loader.getResources(prefix);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            res.add(url);
        }
    }


    /**
     * 校验URL是否允许加载。若出现重复的class，则不允许加载
     * @param loader 类加载器
     * @param url   url
     */
    public static Result<List<String>, URL> validUrl(ClassLoader loader, URL url) {
        String protocol = url.getProtocol();
        JarFile jarFile = null;
        List<String> names = new ArrayList<>();
        try {
            if ("file".equals(protocol) && url.getFile().endsWith(".jar")) {
                jarFile = new JarFile(new File(url.getFile()));
            } else if("jar".equals(protocol)) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                jarFile = connection.getJarFile();
            } else {
                return Result.success();
            }
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }

                URL resource = loader.getResource(name);
                if (resource != null) {
                    String className = name.substring(0, name.length() - 6).replaceAll("/", ".");
                    if ("module-info".equals(className)) {
                        continue;
                    }
                    names.add(className);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
        return names.isEmpty() ? Result.success() : Result.<List<String>, URL>builder()
                .isSuccess(false)
                .data(names)
                .param(url)
                .build();
    }
}
