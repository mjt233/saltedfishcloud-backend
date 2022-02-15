package com.xiaotao.saltedfishcloud.ext;

import java.net.URL;
import java.util.Set;

public interface ExtLoader {
    /**
     * 获取所有已加载的拓展URL集合
     * @return 只读Set集合
     */
    Set<URL> getLoadedExt();

    /**
     * 添加拓展Jar包URL并加载。
     * 注：相同的URL不应该重复加载。
     * @param url   要加载的拓展URL
     */
    void addURL(URL url);

    /**
     * 加载未被加载的拓展
     */
    default void loadAll() {
        for (URL url : getAvailableExtUrl()) {
            addURL(url);
        }
    }

    /**
     * 获取所有可加载的拓展URL
     * @return  拓展URL集合
     */
    Set<URL> getAvailableExtUrl();
}
