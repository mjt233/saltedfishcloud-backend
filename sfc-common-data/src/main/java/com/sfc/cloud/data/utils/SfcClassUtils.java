package com.sfc.cloud.data.utils;

import com.sfc.cloud.model.SfcResult;
import com.sfc.cloud.utils.SfcStringUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * 共享数据访问基座的类与注解反射工具。
 *
 * <p>迁移自宿主的 {@code com.xiaotao.saltedfishcloud.utils.ClassUtils}（驼峰命名工具部分
 * 来自宿主的 {@code StringUtils}），并按「零宿主依赖」原则做了<b>按需裁剪</b>：
 * 只保留全仓库确实在用、且不依赖宿主上下文的成员。</p>
 *
 * <p>返回值类型上有两处刻意的解耦：</p>
 * <ul>
 *   <li>{@link #validUrl(ClassLoader, URL)} 的返回值由宿主模型
 *       {@code com.xiaotao.saltedfishcloud.model.Result} 改为共享库的 {@link SfcResult}；</li>
 *   <li>{@link #getClassEntityFieldGetter(Class)} 的返回元素类型由 Reactor 的 {@code Tuple2}
 *       改为本类的 {@link ColumnGetter} 记录，避免为此引入 reactor 依赖。</li>
 * </ul>
 *
 * <p>本类同时收拢了实体列名推导与批量插入所需的驼峰命名工具方法
 * （{@link #camelToUnder(String)} / {@link #camelToUpperCamel(String)} /
 * {@link #camelToLowerCamel(String)} / {@link #parseCamelAndJoin(String, char)}），
 * 使其在整个模块内只有一份实现，{@link SfcDBUtils} 直接复用。</p>
 *
 * @see SfcFunc
 * @see SfcDBUtils
 */
public class SfcClassUtils {

    /**
     * 实体类字段列表缓存，避免批量插入时反复进行反射扫描。
     */
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
     * 从bean中获取类注解，优先级：类本身 &gt; 父类 &gt; 实现接口<br>
     * 注意：该方法不会缓存结果
     * @param clazz 待解析类
     * @param annotation 要获取的注解类型
     * @param <T> 注解类型
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
        SfcClassUtils.visitExtendsPath(clazz, visitor);
        if (reference.get() != null) {
            return reference.get();
        }

        // 尝试从实现的接口获取注解
        SfcClassUtils.visitImplementsPath(clazz, visitor);
        return reference.get();
    }

    /**
     * 获取实体类的物理数据表名。
     *
     * <p>迁移自宿主 {@code ClassUtils.getEntityTableName}：优先取 {@link Table} 注解的 name，
     * 否则由类名转下划线命名。对编译器生成的 {@code Outer$Inner} 形式类名，
     * 只取 {@code $} 之前的部分。</p>
     *
     * @param clazz 实体类
     * @return 物理数据表名
     */
    public static String getEntityTableName(Class<?> clazz) {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        // 等价于宿主 StringUtils.hasText(tableAnnotation.name())（注解属性不会为 null）
        if (tableAnnotation != null && !tableAnnotation.name().isBlank()) {
            return tableAnnotation.name();
        }
        String simpleName = clazz.getSimpleName();
        int i = simpleName.indexOf("$");
        if (i >= 0) {
            simpleName = simpleName.substring(0, i);
        }
        return camelToUnder(simpleName);
    }

    /**
     * 获取类的所有字段，包括父类。
     *
     * <p>顺序为：子类自身声明的字段 → 逐级向上父类声明的字段（不含 {@code Object}）。
     * 该顺序直接决定批量插入时 SQL 的列顺序，需与宿主保持一致。</p>
     *
     * <p>字段列表按类缓存，避免批量插入时反复进行反射扫描。</p>
     *
     * @param clazz 待扫描的类
     * @return 字段列表
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
     * 实体字段的「列名 + getter 方法」二元组。
     *
     * <p>宿主此处使用的是 Reactor 的 {@code Tuple2<String, Method>}；本模块刻意不引入 reactor，
     * 故以本记录替代，语义与元素顺序完全一致。</p>
     *
     * @param columnName 数据库列名（下划线命名）
     * @param getter     该字段的 getter 方法，用于批量插入时反射取值
     */
    public record ColumnGetter(String columnName, Method getter) {
    }

    /**
     * 获取类的数据表实体字段及其 getter 方法。
     *
     * <p>迁移自宿主 {@code ClassUtils.getClassEntityFieldGetter}，唯一改动是把返回元素类型由
     * Reactor 的 {@code Tuple2<String, Method>} 换成本类的 {@link ColumnGetter}。</p>
     *
     * <p>过滤规则（与宿主一致）：跳过 {@link Transient} 注解、{@code transient} 与 {@code static}
     * 字段，以及 {@link Column#insertable()} 为 {@code false} 的字段。</p>
     *
     * @param clazz 实体类
     * @return 字段的「列名 + getter方法」列表，顺序与 {@link #getAllFields(Class)} 一致
     * @throws IllegalArgumentException 当字段缺少对应的 getter 方法时抛出
     */
    public static List<ColumnGetter> getClassEntityFieldGetter(Class<?> clazz) {
        List<Field> fieldList = getAllFields(clazz);
        return fieldList.stream()
                .filter(f -> f.getAnnotation(Transient.class) == null && !Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()))
                .filter(f -> Optional.ofNullable(f.getAnnotation(Column.class)).map(Column::insertable).orElse(true))
                .map(f -> {
                    try {
                        return new ColumnGetter(camelToUnder(f.getName()), clazz.getMethod("get" + camelToUpperCamel(f.getName())));
                    } catch (NoSuchMethodException e) {
                        throw new IllegalArgumentException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 校验URL是否允许加载。若出现重复的class，则不允许加载。
     *
     * <p>迁移自宿主 {@code ClassUtils.validUrl}，唯一改动是返回值由宿主模型
     * {@code com.xiaotao.saltedfishcloud.model.Result} 换成共享库的 {@link SfcResult}，
     * 成功/失败判定、{@code data} 与 {@code param} 的语义与宿主完全一致。</p>
     *
     * @param loader 类加载器
     * @param url    待校验的 jar 资源URL；非 jar 协议时直接返回成功
     * @return 校验结果：jar 内无「已能被 {@code loader} 加载」的类时返回 {@link SfcResult#success()}；
     *         否则返回成功标志为 {@code false} 的结果，{@code data} 为重复类名列表、{@code param} 为原始 url
     */
    public static SfcResult<List<String>, URL> validUrl(ClassLoader loader, URL url) {
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
                return SfcResult.success();
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
        return names.isEmpty() ? SfcResult.success() : SfcResult.<List<String>, URL>builder()
                .isSuccess(false)
                .data(names)
                .param(url)
                .build();
    }

    /**
     * 获取类的短名并转为 JavaBean 属性名（首字母小写）。
     *
     * <p>供 {@code SpringContextUtils.registerBean(Class)} 推导默认 Bean 名称使用。</p>
     *
     * <p>注意：原调用点解析到的其实是 Spring 的
     * {@link org.springframework.util.ClassUtils#getShortNameAsProperty(Class)}（该文件的 import
     * 指向 Spring 而非宿主 {@code ClassUtils}）。为保证短名推导规则（CGLIB {@code $$} 分隔符、
     * 内部类 {@code $} 分隔符、首字母小写规则）与本模块消费方完全一致，这里直接委派给同一实现，
     * 而不是自行重写。</p>
     *
     * @param clazz 待处理的类
     * @return 首字母小写的短类名
     */
    public static String getShortNameAsProperty(Class<?> clazz) {
        return ClassUtils.getShortNameAsProperty(clazz);
    }

    /**
     * 访问类直到Object继承路径
     * @param clazz     待测试类
     * @param consumer  访问函数，返回值表示是否继续遍历
     */
    public static void visitExtendsPath(Class<?> clazz, Function<Class<?>, Boolean> consumer) {
        Class<?> curClass = clazz;
        while (curClass != null && curClass != Object.class) {
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
     * 根据继承的父类获取泛型类型
     * @param o     泛型对象
     * @param index 泛型索引
     * @return      该索引位置上的具体类型
     * @throws IllegalArgumentException 当对象的直接父类不是参数化类型时抛出
     */
    public static Class<?> getTypeParameterBySuperClass(Object o, int index) {
        Type type = o.getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            Type actualTypeArgument = ((ParameterizedType) type).getActualTypeArguments()[index];
            return getClassByType(actualTypeArgument);
        }
        throw new IllegalArgumentException("找不到" + o + "的泛型");
    }

    /**
     * 把泛型类型解析为具体的Class对象。
     *
     * @param type 泛型类型
     * @return 具体类型
     * @throws RuntimeException 当无法解析出具体Class时抛出
     */
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
     * @return      第一个泛型位置上的具体类型
     */
    public static Class<?> getTypeParameterBySuperClass(Object o) {
        return getTypeParameterBySuperClass(o, 0);
    }

    /**
     * Getter方法Lambda表达式的元数据。
     *
     * @param fieldName   字段名（小驼峰）
     * @param method      字段的Getter方法
     * @param field       字段对象
     * @param entityClass 字段所属的实体类
     */
    public record LambdaMetaData(
            String fieldName,
            Method method,
            Field field,
            Class<?> entityClass
    ){}

    /**
     * 根据Getter方法的Lambda表达式解析对应的class和field。<br>
     * e.g.
     * <pre><code>
     * SfcClassUtils.parseGetterLambdaMetaData(SomeClass::getSonField);
     * </code></pre>
     * 实现参考 <a href="https://www.cnblogs.com/xw-01/p/18308286">从Mybatis-Plus开始认识SerializedLambda - 二价亚铁 - 博客园</a>
     *
     * @param func Getter方法的Lambda表达式
     * @param <T>  实体类类型
     * @param <R>  字段类型
     * @return 解析得到的Lambda元数据
     * @throws RuntimeException 当lambda无法解析或方法名不符合 {@code is}/{@code get} 规范时抛出
     */
    public static <T,R> LambdaMetaData parseGetterLambdaMetaData(SfcFunc<T, R> func) {
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
                fieldName = camelToLowerCamel(methodName.substring(3));
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

    /**
     * 驼峰命名转为下划线命名。
     *
     * <p>委托 {@link SfcStringUtils#camelToUnder(String)}：驼峰转换在本仓只保留<b>一份实现</b>
     * （{@code sfc-common} 的 {@code SfcStringUtils}），本类只做转发，避免两个模块各存一份。</p>
     *
     * @param str   待转换字符串
     * @return      下划线命名结果
     */
    public static String camelToUnder(String str) {
        return SfcStringUtils.camelToUnder(str);
    }

    /**
     * 转为大驼峰。
     *
     * <p>委托 {@link SfcStringUtils#camelToUpperCamel(String)}，理由同 {@link #camelToUnder(String)}。</p>
     *
     * @param str   待转换小驼峰字符串
     * @return      转换后的大驼峰字符串
     */
    public static String camelToUpperCamel(String str) {
        return SfcStringUtils.camelToUpperCamel(str);
    }

    /**
     * 转为小驼峰。
     *
     * <p>委托 {@link SfcStringUtils#camelToLowerCamel(String)}，理由同 {@link #camelToUnder(String)}。</p>
     *
     * @param str   待转换大驼峰字符串
     * @return      转换后的小驼峰字符串
     */
    public static String camelToLowerCamel(String str) {
        return SfcStringUtils.camelToLowerCamel(str);
    }

    /**
     * 解析驼峰命名，并将各单词转为小写后用参数连接。
     *
     * <p>委托 {@link SfcStringUtils#parseCamelAndJoin(String, char)}，理由同 {@link #camelToUnder(String)}。</p>
     *
     * @param str   待解析字符串
     * @param ch    连接字符
     * @return      处理结果
     */
    public static String parseCamelAndJoin(String str, char ch) {
        return SfcStringUtils.parseCamelAndJoin(str, ch);
    }
}
