package com.sfc.ext.webdav.store.filesystem;

import com.sfc.ext.webdav.store.handler.WebDavStorage;
import com.sfc.ext.webdav.store.model.WebDavClientProperty;
import com.xiaotao.saltedfishcloud.service.file.AbstractStorageFactory;
import com.xiaotao.saltedfishcloud.service.file.StorageMetadata;
import com.xiaotao.saltedfishcloud.service.file.store.ScopedStorage;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class WebDavStorageFactory extends AbstractStorageFactory<WebDavClientProperty, Storage> {
    @Override
    public WebDavClientProperty parseProperty(Map<String, Object> params) {
        return ObjectUtils.mapToBean(params, WebDavClientProperty.class);
    }

    @Override
    public Storage generateStorage(WebDavClientProperty property) throws IOException {
        return new ScopedStorage(
                new WebDavStorage(property),
                Optional.ofNullable(property.getBasePath()).filter(StringUtils::hasText).orElse("/")
        );
    }

    @Override
    public StorageMetadata getMetadata() {
        return StorageMetadata.builder()
                .describe("第三方系统 WebDAV 存储服务")
                .name("WebDAV")
                .isPublic(true)
                .protocol("webdav")
                .configNode(PropertyUtils.getConfigNodeFromEntityClass(WebDavClientProperty.class).values())
                .build();
    }
}
