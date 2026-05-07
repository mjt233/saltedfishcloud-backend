package com.sfc.ext.webdav.store.filesystem;

import com.sfc.ext.webdav.store.handler.WebDavStoreRawHandler;
import com.sfc.ext.webdav.store.model.WebDavClientProperty;
import com.xiaotao.saltedfishcloud.service.file.AbstractRawDiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import com.xiaotao.saltedfishcloud.utils.SpringContextUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class WebDavStoreFileSystemFactory extends AbstractRawDiskFileSystemFactory<WebDavClientProperty, DiskFileSystem> {
    @Override
    public WebDavClientProperty parseProperty(Map<String, Object> params) {
        return ObjectUtils.mapToBean(params, WebDavClientProperty.class);
    }

    @Override
    public DiskFileSystem generateDiskFileSystem(WebDavClientProperty property) throws IOException {
        RawDiskFileSystem diskFileSystem = new RawDiskFileSystem(
                new WebDavStoreRawHandler(property),
                Optional.ofNullable(property.getBasePath()).filter(StringUtils::hasText).orElse("/")
        );
        diskFileSystem.setThumbnailService(SpringContextUtils.getContext().getBean(ThumbnailService.class));
        return diskFileSystem;
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DiskFileSystemDescribe.builder()
                .describe("第三方系统 WebDAV 存储服务")
                .name("WebDAV")
                .isPublic(true)
                .protocol("webdav")
                .configNode(PropertyUtils.getConfigNodeFromEntityClass(WebDavClientProperty.class).values())
                .build();
    }
}
