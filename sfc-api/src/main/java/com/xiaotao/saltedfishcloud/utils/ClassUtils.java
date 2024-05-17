package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.Result;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ClassUtils {
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 从类中获取指定注解的类访问器工厂
     *
     * @param annotation 要获取的注解
     * @return 参数 - 接收注解的引用
     */
    private static <T extends Annotation> Function<AtomicReference<T>, Function<Class<?>, Boolean>> getAnnotationGetterFactory(Class<T> annotation) {
        return ref -> clazz -> {
            T ann = clazz.getAnnotation(annotation);
            if (ann != null) {
                ref.set(ann);
                return false;
            }
            return true;
        };
    }

    /**
     * 从bean中获取类注解，优先级：类本身 > 父类 > 实现接口<br>
     * 注意：该方法不会缓存结果
     * @param clazz 待解析类
     * @return      获取到的注解，若获取不到则返回null
     */
    public static <T extends Annotation> T getAnnotation(Class<?> clazz, Class<T> annotation) {
        // 尝试直接从类上获取注解
        T anno = clazz.getAnnotation(annotation);
        if (anno != null) {
            return anno;
        }

        // 尝试从继承的父类获取注解
        AtomicReference<T> reference = new AtomicReference<>();
        Function<Class<?>, Boolean> visitor = getAnnotationGetterFactory(annotation).apply(reference);
        ClassUtils.visitExtendsPath(clazz, visitor);
        if (reference.get() != null) {
            return reference.get();
        }

        // 尝试从实现的接口获取注解
        ClassUtils.visitImplementsPath(clazz, visitor);
        return reference.get();
    }

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
            curClass = curClass.getSuperclass();
        }
        return res;
    }

    /**
     * 访问类直到Object继承路径
     * @param clazz     待测试类
     * @param consumer  访问函数，返回值表示是否继续遍历
     */
    public static void visitExtendsPath(Class<?> clazz, Function<Class<?>, Boolean> consumer) {
        Class<?> curClass = clazz;
        while (curClass != Object.class) {
            boolean isContinue = Boolean.TRUE.equals(consumer.apply(curClass));
            if (!isContinue) {
                return;
            }
            curClass = curClass.getSuperclass();
        }
    }

    /**
     * 访问类的所有实现接口
     * @param clazz     待测试类
     * @param consumer  访问函数，返回值表示是否继续遍历
     */
    public static void visitImplementsPath(Class<?> clazz, Function<Class<?>, Boolean> consumer) {
        LinkedList<Class<?>> queue = new LinkedList<>(Arrays.asList(clazz.getInterfaces()));
        while (!queue.isEmpty()) {
            Class<?> anInterface = queue.pop();
            boolean isContinue = Boolean.TRUE.equals(consumer.apply(anInterface));
            if (!isContinue) {
                continue;
            }
            queue.addAll(Arrays.asList(anInterface.getInterfaces()));
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
