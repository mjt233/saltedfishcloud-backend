package com.sfc.ext.oss;

import com.xiaotao.saltedfishcloud.service.file.AbstractRawStorageFactory;
import com.xiaotao.saltedfishcloud.service.file.StorageMetadata;
import com.xiaotao.saltedfishcloud.service.file.store.ScopedStorage;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@RequiredArgsConstructor
public class OSSStorageFactory extends AbstractRawStorageFactory<OSSProperty, Storage> {
    /**
     * 兼容现有构造注入的缩略图服务。
     */
    private final ThumbnailService thumbnailService;

    private final Map<String, Function<OSSProperty, Storage>> factoryMap = new ConcurrentHashMap<>();

    /**
     * 注册一个新的OSS存储类型
     * @param type  OSS类型
     * @param factory   存储工厂方法
     */
    public void registerOSSStoreType(String type, Function<OSSProperty, Storage> factory) {
        if(factoryMap.putIfAbsent(type, factory) != null) {
            throw new IllegalArgumentException("类型为" + type + "的OSS存储已注册");
        }
    }

    @Override
    public StorageMetadata getMetadata() {
        return StorageMetadata.builder()
                .isPublic(true)
                .protocol("oss")
                .name("OSS对象存储")
                .describe("使用第三方云服务提供商提供的对象存储服务")
                .configNode(
                        new ArrayList<>(PropertyUtils.getConfigNodeFromEntityClass(OSSProperty.class)
                                .values())
                )
                .build();
    }

    @Override
    public OSSProperty parseProperty(Map<String, Object> params) {
        return ObjectUtils.mapToBean(params, OSSProperty.class);
    }

    @Override
    public Storage generateStorage(OSSProperty property) throws IOException {
        Function<OSSProperty, Storage> storeFactory = Objects.requireNonNull(factoryMap.get(property.getType()), "未注册类型为" + property.getType() + "的OSS存储");
        Storage rawStoreHandler = storeFactory.apply(property);
        return new ScopedStorage(rawStoreHandler, "/");
    }
}
