package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.*;

class DirPathClassLoaderTest {

    @Test
    public void testLoad() throws IOException, ClassNotFoundException {
        Path path = Paths.get("").toAbsolutePath().getParent().resolve("sfc-ext/sfc-ext-minio-store/target/classes");
        DirPathClassLoader loader = new DirPathClassLoader(path, DirPathClassLoaderTest.class.getClassLoader());
        Enumeration<URL> resources = loader.getResources(PluginManager.PLUGIN_INFO_FILE);
        if (resources.hasMoreElements()) {
            System.out.println(resources.nextElement());
            PluginInfo pluginInfo = MapperHolder.parseJson(ExtUtils.getResourceText(loader, PluginManager.PLUGIN_INFO_FILE), PluginInfo.class);
            assertNotNull(pluginInfo.getName());
        } else {
            fail();
        }
        assertNotNull(loader.loadClass("java.lang.Integer"));

        // 重复加载查看DEBUG日志 验证是否会被JVM缓存class，findClass没被重复调用
        assertNotNull(loader.loadClass("com.saltedfishcloud.ext.minio.MinioStoreService"));
        assertNotNull(loader.loadClass("com.saltedfishcloud.ext.minio.MinioStoreService"));
    }
}