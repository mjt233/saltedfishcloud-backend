package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public abstract class AbstractRawStorageFactory<T, D extends DiskFileSystem> implements StorageFactory {
    private final Map<T, D> CACHE = new ConcurrentHashMap<>();

    @Override
    public DiskFileSystem getFileSystem(Map<String, Object> params) throws FileSystemParameterException {
        T property = parseProperty(params);
        return CACHE.computeIfAbsent(property, key -> {
            try {
                return generateDiskFileSystem(property);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void testFileSystem(DiskFileSystem fileSystem) throws FileSystemParameterException {
        try {
            fileSystem.exist(0, "/");
        } catch (IOException e) {
            throw new FileSystemParameterException("测试失败",e);
        }

    }

    @Override
    public void clearCache(Collection<Map<String, Object>> params) {
        Set<T> paramSet = params.stream().map(this::parseProperty).collect(Collectors.toSet());
        for (Map.Entry<T, D> entry : CACHE.entrySet()) {
            try {
                if (!paramSet.contains(entry.getKey())) {
                    D diskFileSystem = entry.getValue();
                    if (diskFileSystem instanceof Closeable closeable) {
                        closeable.close();
                    }
                    CACHE.remove(entry.getKey());
                }
            } catch (IOException err) {
                log.error("清理文件系统缓存失败", err);
            }
        }
    }

    @Override
    public void clearCache(Map<String, Object> params) {
        T property = this.parseProperty(params);
        D diskFileSystem = CACHE.get(property);
        if (diskFileSystem == null) {
            log.warn("文件系统缓存不存在: {}", params);
            return;
        }
        try {
            if (diskFileSystem instanceof Closeable closeable) {
                closeable.close();
            }
            CACHE.remove(property);
        } catch (IOException e) {
            log.error("清理文件系统缓存失败", e);
        }
    }

    public abstract T parseProperty(Map<String, Object> params);

    public abstract D generateDiskFileSystem(T property) throws IOException;


}
