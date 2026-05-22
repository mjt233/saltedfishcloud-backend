package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 抽象的原始存储工厂类，用于快速构建支持缓存机制的挂载存储工厂。
 *
 * @param <T> 参数实体类型
 * @param <D> 存储类型
 */
@Slf4j
public abstract class AbstractStorageFactory<T, D extends Storage> implements StorageFactory {
    private final Map<T, D> CACHE = new ConcurrentHashMap<>();

    @Override
    public Storage getStorage(Map<String, Object> params) throws FileSystemParameterException {
        T property = parseProperty(params);
        return CACHE.computeIfAbsent(property, key -> {
            try {
                return generateStorage(property);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void testStorage(Storage storage) throws FileSystemParameterException {
        try {
            storage.exist("/");
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
                    D storage = entry.getValue();
                    storage.close();
                    CACHE.remove(entry.getKey());
                }
            } catch (Exception err) {
                log.error("清理存储缓存失败", err);
            }
        }
    }

    @Override
    public void clearCache(Map<String, Object> params) {
        T property = this.parseProperty(params);
        D storage = CACHE.get(property);
        if (storage == null) {
            log.warn("存储缓存不存在: {}", params);
            return;
        }
        try {
            storage.close();
            CACHE.remove(property);
        } catch (Exception e) {
            log.error("清理存储缓存失败", e);
        }
    }

    /**
     * 将参数映射解析为存储属性对象。
     *
     * @param params 原始参数
     * @return 解析后的属性对象
     */
    public abstract T parseProperty(Map<String, Object> params);

    /**
     * 根据属性创建对应的存储实例。
     *
     * @param property 存储属性
     * @return 存储实例
     * @throws IOException 创建失败时抛出
     */
    public abstract D generateStorage(T property) throws IOException;


}
