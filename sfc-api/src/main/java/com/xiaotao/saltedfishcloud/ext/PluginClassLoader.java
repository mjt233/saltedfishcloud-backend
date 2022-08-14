package com.xiaotao.saltedfishcloud.ext;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

public abstract class PluginClassLoader extends URLClassLoader {


    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    /**
     * 获取所有已加载的拓展URL集合
     * @return 只读Set集合
     */
    public abstract Set<URL> getLoadedExt();

    /**
     * 添加拓展Jar包URL并加载。
     * 注：相同的URL不应该重复加载。
     * @param url   要加载的拓展URL
     */
    public abstract void loadFromUrl(URL url);

    /**
     * 加载未被加载的拓展
     */
    public void loadAll() {
        for (URL url : getAvailableExtUrl()) {
            loadFromUrl(url);
        }
    }

    /**
     * 获取所有可加载的拓展URL
     * @return  拓展URL集合
     */
    public abstract Set<URL> getAvailableExtUrl();
}
