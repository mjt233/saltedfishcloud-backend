package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.Result;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassUtils {
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

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
     * 获取类的所有字段，包括父类
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        Class<?> curClass = clazz;
        List<Field> res = new ArrayList<>();
        while (curClass != Object.class) {
            Class<?> finalCurClass = curClass;
            List<Field> fieldList = FIELD_CACHE.computeIfAbsent(curClass, k -> List.of(finalCurClass.getDeclaredFields()));

            res.addAll(fieldList);
            curClass = clazz.getSuperclass();
        }
        return res;
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
                    if (className.endsWith("module-info")) {
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
