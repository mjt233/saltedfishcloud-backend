package com.sfc.ext.oss;

import com.xiaotao.saltedfishcloud.service.file.AbstractRawDiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class OSSDiskFileSystemFactory extends AbstractRawDiskFileSystemFactory<OSSProperty, DiskFileSystem> {

    private final Map<String, Function<OSSProperty, DirectRawStoreHandler>> factoryMap = new ConcurrentHashMap<>();

    /**
     * 注册一个新的OSS存储类型
     * @param type  OSS类型
     * @param factory   存储工厂方法
     */
    public void registerOSSStoreType(String type, Function<OSSProperty, DirectRawStoreHandler> factory) {
        if(factoryMap.putIfAbsent(type, factory) != null) {
            throw new IllegalArgumentException("类型为" + type + "的OSS存储已注册");
        }
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DiskFileSystemDescribe.builder()
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
    public DiskFileSystem generateDiskFileSystem(OSSProperty property) throws IOException {
        Function<OSSProperty, DirectRawStoreHandler> storeFactory = Objects.requireNonNull(factoryMap.get(property.getType()), "未注册类型为" + property.getType() + "的OSS存储");
        DirectRawStoreHandler rawStoreHandler = storeFactory.apply(property);
        return new RawDiskFileSystem(rawStoreHandler, "/");
    }
}
