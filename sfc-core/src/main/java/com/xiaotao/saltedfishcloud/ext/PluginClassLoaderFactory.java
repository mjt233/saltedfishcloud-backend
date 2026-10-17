package com.xiaotao.saltedfishcloud.ext;


import com.sfc.common.annotations.ConfigProperty;
import com.sfc.common.annotations.ConfigPropertyEntity;
import com.sfc.common.annotations.ConfigPropertiesGroup;
import com.sfc.common.annotations.ConfigSelectOption;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class PluginClassLoaderFactory {
    private final static Map<String, Class<?>> API_ANNOTATIONS_MAP = Stream.of(
            ConfigPropertyEntity.class,
            ConfigProperty.class,
            ConfigPropertiesGroup.class,
            ConfigSelectOption.class
    ).collect(Collectors.toMap(Class::getName, Function.identity()));

    /**
     * 创建纯净的插件类加载器，仅用于读取插件信息使用。除部分插件信息相关的部分注解类以及插件内部本身的类外，无法读取到其他资源。
     * @param url   插件资源URL
     * @return      插件类加载器
     */
    public static URLClassLoader createPurePluginClassLoader(URL url) {
        return new URLClassLoader(new URL[]{url}, null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                Class<?> clazz = API_ANNOTATIONS_MAP.get(name);
                if (clazz != null) {
                    return clazz;
                }
                return super.findClass(name);
            }
        };
    }
}
