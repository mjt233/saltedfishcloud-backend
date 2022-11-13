package com.xiaotao.saltedfishcloud.ext;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地路径目录类加载器，用于将某个指定的目录作为一个类加载路径，解决UrlClassLoader无法从本地文件系统中读取资源的问题
 */
@Slf4j
public class DirPathClassLoader extends ClassLoader {
    private final Path basePath;

    /**
     *
     * @param path      作为类加载路径的本地文件系统目录路径
     * @param parent    指定父加载器
     */
    public DirPathClassLoader(Path path, ClassLoader parent) {
        super(parent);
        this.checkPath(path);
        this.basePath = path;
    }

    /**
     * 使用Bootstrap ClassLoader作为父加载器
     * @param path  作为类加载路径的本地文件系统目录路径
     */
    public DirPathClassLoader(Path path) {
        this(path, null);
    }

    private void checkPath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path不能为null");
        }
        if(!Files.isDirectory(path)) {
            throw new IllegalArgumentException(path + " 不是目录！");
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        log.debug("{}尝试查找：{}", "[DirPathClassLoader]", name);
        String requirePath = name.replaceAll("\\.", "/") + ".class";
        Path targetPath = basePath.resolve(Paths.get(requirePath));
        if (!Files.exists(targetPath) || Files.isDirectory(targetPath)) {
            throw new ClassNotFoundException(name);
        }
        try(InputStream is = Files.newInputStream(targetPath)) {
            byte[] bytes = StreamUtils.copyToByteArray(is);
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ClassNotFoundException(name, e);
        }
    }

    @Nullable
    @Override
    public URL getResource(String name) {
        return super.getResource(name);
    }


    @Override
    protected URL findResource(String name) {
        try {
            Path targetPath = basePath.resolve(name);
            if (Files.exists(targetPath)) {
                return targetPath.toUri().toURL();
            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return super.findResource(name);
        }
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        URL url = findResource(name);
        if (url == null) {
            return Collections.emptyEnumeration();
        } else {
            return new Enumeration<>() {
                private final Iterator<URL> iterator;

                {
                    iterator = Collections.singletonList(url).iterator();
                }

                @Override
                public boolean hasMoreElements() {
                    return iterator.hasNext();
                }

                @Override
                public URL nextElement() {
                    return iterator.next();
                }
            };
        }
    }
}
