package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 抽象的原始文件系统工厂类，用于快速构建支持缓存机制的用于挂载的外部文件系统工厂
 * @param <T>   参数实体类型
 * @param <D>   文件系统类型
 */
public abstract class AbstractRawDiskFileSystemFactory<T, D extends DiskFileSystem> implements DiskFileSystemFactory {
    private final Map<T, D> CACHE = new ConcurrentHashMap<>();

    @Override
    public DiskFileSystem getFileSystem(Map<String, Object> params) throws FileSystemParameterException {
        T property = parseProperty(params);
        return CACHE.computeIfAbsent(property, key -> generateDiskFileSystem(property));
    }

    @Override
    public void testFileSystem(DiskFileSystem fileSystem) throws FileSystemParameterException {
        fileSystem.exist(0, "/");
    }

    @Override
    public void clearCache(Collection<Map<String, Object>> params) {
        Set<T> paramSet = params.stream().map(this::parseProperty).collect(Collectors.toSet());
        for (Map.Entry<T, D> entry : CACHE.entrySet()) {
            try {
                if (!paramSet.contains(entry.getKey())) {
                    D diskFileSystem = entry.getValue();
                    if (diskFileSystem instanceof Closeable) {
                        ((Closeable) diskFileSystem).close();
                    }
                    CACHE.remove(entry.getKey());
                }
            } catch (IOException err) {
                err.printStackTrace();
            }
        }
    }

    public abstract T parseProperty(Map<String, Object> params);

    public abstract D generateDiskFileSystem(T property);


}
