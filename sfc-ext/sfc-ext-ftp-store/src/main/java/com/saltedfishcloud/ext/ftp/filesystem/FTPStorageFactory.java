package com.saltedfishcloud.ext.ftp.filesystem;

import com.saltedfishcloud.ext.ftp.FTPStorage;
import com.saltedfishcloud.ext.ftp.FTPProperty;
import com.xiaotao.saltedfishcloud.service.file.AbstractStorageFactory;
import com.xiaotao.saltedfishcloud.service.file.StorageMetadata;
import com.xiaotao.saltedfishcloud.service.file.store.ScopedStorage;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import lombok.Setter;

import java.io.IOException;
import java.util.Map;


public class FTPStorageFactory extends AbstractStorageFactory<FTPProperty, Storage> {
    private static final StorageMetadata DESCRIBE = StorageMetadata.builder()
            .isPublic(true)
            .protocol("ftp")
            .name("FTP文件传输")
            .describe("FTP文件传输（实验性功能）")
            .configNode(PropertyUtils.getConfigNodeFromEntityClass(FTPProperty.class).values())
            .build();

    /**
     * 兼容现有自动配置注入的缩略图服务。
     */
    @Setter
    private ThumbnailService thumbnailService;

    @Override
    public FTPProperty parseProperty(Map<String, Object> params) {
        return CollectionUtils.validMap(params)
                .addField("hostname", "path")
                .validAndToBean(FTPProperty.class);
    }

    @Override
    public Storage generateStorage(FTPProperty property) throws IOException {
        return new ScopedStorage(new FTPStorage(property), property.getPath());
    }

    @Override
    public StorageMetadata getMetadata() {
        return DESCRIBE;
    }
}
