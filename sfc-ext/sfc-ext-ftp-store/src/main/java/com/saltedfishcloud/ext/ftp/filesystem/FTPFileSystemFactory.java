package com.saltedfishcloud.ext.ftp.filesystem;

import com.saltedfishcloud.ext.ftp.FTPDirectRawStoreHandler;
import com.saltedfishcloud.ext.ftp.FTPProperty;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.AbstractRawDiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import lombok.Setter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;


public class FTPFileSystemFactory extends AbstractRawDiskFileSystemFactory<FTPProperty, RawDiskFileSystem> {
    private static final DiskFileSystemDescribe DESCRIBE = DiskFileSystemDescribe.builder()
            .isPublic(true)
            .protocol("ftp")
            .name("FTP文件传输")
            .describe("FTP文件传输（实验性功能）")
            .configNode(Collections.singletonList(ConfigNode.builder()
                    .title("基本信息")
                    .name("base")
                    .nodes(Arrays.asList(
                            ConfigNode.builder().name("hostname").inputType("text").required(true).build(),
                            ConfigNode.builder().name("port").inputType("text").required(true).build(),
                            ConfigNode.builder().name("username").inputType("text").build(),
                            ConfigNode.builder().name("password").inputType("text").isMask(true).build(),
                            ConfigNode.builder().name("path").inputType("text").required(true).build(),
                            ConfigNode.builder().name("usePassive").title("").describe("使用被动模式").inputType("switch").required(true).build()
                    ))
                    .build()))
            .build();
    @Setter
    private ThumbnailService thumbnailService;
    @Override
    public FTPProperty parseProperty(Map<String, Object> params) {
        return CollectionUtils.validMap(params)
                .addField("username", "password", "hostname", "path")
                .validAndToBean(FTPProperty.class);
    }

    @Override
    public RawDiskFileSystem generateDiskFileSystem(FTPProperty property) throws IOException {
        FTPDirectRawStoreHandler handler = new FTPDirectRawStoreHandler(property);
        RawDiskFileSystem fileSystem = new RawDiskFileSystem(handler, property.getPath());
        fileSystem.setThumbnailService(thumbnailService);
        return fileSystem;
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }
}
