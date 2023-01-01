package com.saltedfishcloud.ext.ftp.filesystem;

import com.saltedfishcloud.ext.ftp.FTPDirectRawStoreHandler;
import com.saltedfishcloud.ext.ftp.FTPProperty;
import com.xiaotao.saltedfishcloud.service.file.AbstractRawDiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.PropertyUtils;
import lombok.Setter;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Map;


public class FTPFileSystemFactory extends AbstractRawDiskFileSystemFactory<FTPProperty, RawDiskFileSystem> {
    private static final DiskFileSystemDescribe DESCRIBE = DiskFileSystemDescribe.builder()
            .isPublic(true)
            .protocol("ftp")
            .name("FTP文件传输")
            .describe("FTP文件传输（实验性功能）")
            .configNode(PropertyUtils.getConfigNodeFromEntityClass(FTPProperty.class).values())
            .build();
    @Setter
    private ThumbnailService thumbnailService;
    @Override
    public FTPProperty parseProperty(Map<String, Object> params) {
        return CollectionUtils.validMap(params)
                .addField("hostname", "path")
                .validAndToBean(FTPProperty.class);
    }

    @Override
    public RawDiskFileSystem generateDiskFileSystem(FTPProperty property) throws IOException {
        FTPDirectRawStoreHandler handler = new FTPDirectRawStoreHandler(property);
        RawDiskFileSystem fileSystem = new RawDiskFileSystem(handler, property.getPath());

        if (property.getUseThumbnail()) {
            fileSystem.setThumbnailService(thumbnailService);
        }
        return fileSystem;
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }
}
