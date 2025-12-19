package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.Result;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ClassUtils {
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取实体类的物理数据表名
     */
    public static String getEntityTableName(Class<?> clazz) {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        if (tableAnnotation != null) {
            return tableAnnotation.name();
        }
        String simpleName = clazz.getSimpleName();
        int i = simpleName.indexOf("$");
        if (i >= 0) {
            simpleName = simpleName.substring(0, i);
        }
        return StringUtils.camelToUnder(simpleName);
    }

    /**
     * 获取类的数据表实体字段及其getter方法
     */
    public static List<Tuple2<String, Method>> getClassEntityFieldGetter(Class<?> clazz) {
        List<Field> fieldList = getAllFields(clazz);
        return fieldList.stream()
                .filter(f -> f.getAnnotation(Transient.class) == null && !Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()))
                .filter(f -> Optional.ofNullable(f.getAnnotation(Column.class)).map(Column::insertable).orElse(true))
                .map(f -> {
                    try {
                        return Tuples.of(StringUtils.camelToUnder(f.getName()), clazz.getMethod("get" + StringUtils.camelToUpperCamel(f.getName())));
                    } catch (NoSuchMethodException e) {
                        throw new IllegalArgumentException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    public record LambdaMetaData(
            String fieldName,
            Method method,
            Field field,
            Class<?> entityClass
    ){}

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

    /**
     * 根据继承的父类获取泛型类型
     * @param o     泛型对象
     * @param index 泛型索引
     */
    public static Class<?> getTypeParameterBySuperClass(Object o, int index) {
        Type type = o.getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            Type actualTypeArgument = ((ParameterizedType) type).getActualTypeArguments()[index];
            return getClassByType(actualTypeArgument);
        }
        throw new IllegalArgumentException("找不到" + o + "的泛型");
    }

    private static Class<?> getClassByType(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            return getClassByType(rawType);
        }
        throw new RuntimeException("无法找到" + type + "对应的具体class");
    }


    /**
     * 根据继承的父类获取第一个泛型类型
     * @param o     泛型对象
     */
    public static Class<?> getTypeParameterBySuperClass(Object o) {
        return getTypeParameterBySuperClass(o, 0);
    }

    /**
     * 根据Getter方法的Lambda表达式解析对应的class和field。<br>
     * e.g.
     * <pre><code>
     * ClassUtils.parseGetterLambdaMetaData(SomeClass::getSonField);
     * </code></pre>
     * 实现参考 <a href="https://www.cnblogs.com/xw-01/p/18308286">从Mybatis-Plus开始认识SerializedLambda - 二价亚铁 - 博客园</a>
     */
    public static <T,R> LambdaMetaData parseGetterLambdaMetaData(SFunc<T, R> func) {
        try {
            Method writeReplace = func.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda writeReplaceInvoker = (SerializedLambda) writeReplace.invoke(func);
            Class<?> propertyEntityClass = func.getClass().getClassLoader().loadClass(writeReplaceInvoker.getImplClass().replaceAll("/", "."));
            String methodName = writeReplaceInvoker.getImplMethodName();

            String fieldName;
            if (methodName.startsWith("is")) {
                fieldName = methodName.substring(2);
            } else if (methodName.startsWith("get")) {
                fieldName = StringUtils.camelToLowerCamel(methodName.substring(3));
            } else {
                throw new IllegalArgumentException("类 " + propertyEntityClass + " 的方法名称 " + methodName + "不符合规范，需要为is或get开头");
            }
            Field field = propertyEntityClass.getDeclaredField(fieldName);
            return new LambdaMetaData(
                    fieldName,
                    propertyEntityClass.getDeclaredMethod(methodName),
                    field,
                    propertyEntityClass
            );
        } catch (Exception e) {
            throw new RuntimeException("通过lambda字段信息异常", e);
        }
    }
}
