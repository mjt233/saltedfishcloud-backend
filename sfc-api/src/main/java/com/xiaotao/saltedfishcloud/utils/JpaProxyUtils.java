package com.xiaotao.saltedfishcloud.utils;

import org.hibernate.proxy.HibernateProxy;
import org.springframework.beans.BeanUtils;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * JPA 代理对象工具类。
 * <p>
 * 主要用于处理 Hibernate 延迟加载产生的代理对象，
 * 便于在缓存持久化、序列化或 DTO 转换场景中获得普通对象实例。
 * </p>
 */
public class JpaProxyUtils {

    /**
     * 工具类无需实例化。
     */
    private JpaProxyUtils() {
    }

    /**
     * 判断给定对象是否为 Hibernate 代理对象。
     *
     * @param source 待判断对象
     * @return true 表示对象为 Hibernate 代理
     */
    public static boolean isProxy(Object source) {
        return source instanceof HibernateProxy;
    }

    /**
     * 获取对象对应的实体类型。
     * <p>
     * 若传入的是 Hibernate 代理，则返回代理指向的持久化实体类型；
     * 否则返回对象自身的运行时类型。
     * </p>
     *
     * @param source 待解析对象
     * @return 实体类型；当 source 为 null 时返回 null
     */
    public static Class<?> getEntityClass(Object source) {
        if (source == null) {
            return null;
        }
        Object unwrapped = unwrapProxy(source);
        return unwrapped == null ? null : unwrapped.getClass();
    }

    /**
     * 将 Hibernate 代理对象解包为实际实体对象。
     * <p>
     * 若传入对象并非代理，则直接原样返回。
     * </p>
     *
     * @param source 待解包对象
     * @param <T>    返回对象类型
     * @return 实际实体对象；当 source 为 null 时返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T unwrapProxy(Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof HibernateProxy proxy) {
            return (T) proxy.getHibernateLazyInitializer().getImplementation();
        }
        return (T) source;
    }

    /**
     * 将 Hibernate 代理对象转换为普通对象副本。
     * <p>
     * 该方法会先解包代理，再通过浅拷贝生成一个新的普通对象实例。
     * 适用于需要避免直接暴露持久化代理对象的场景。
     * </p>
     *
     * @param source         待转换对象
     * @param targetSupplier 普通对象构造器
     * @param <T>            目标对象类型
     * @return 转换后的普通对象；当 source 为 null 时返回 null
     */
    public static <T> T toPlainObject(Object source, Supplier<T> targetSupplier) {
        Objects.requireNonNull(targetSupplier, "targetSupplier must not be null");
        T unwrapped = unwrapProxy(source);
        if (unwrapped == null) {
            return null;
        }
        T target = targetSupplier.get();
        BeanUtils.copyProperties(unwrapped, target);
        return target;
    }
}


