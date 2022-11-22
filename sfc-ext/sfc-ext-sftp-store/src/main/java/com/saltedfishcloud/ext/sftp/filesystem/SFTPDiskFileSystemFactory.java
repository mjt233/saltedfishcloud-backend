package com.saltedfishcloud.ext.sftp.filesystem;

import com.saltedfishcloud.ext.sftp.SFTPDirectRawStoreHandler;
import com.saltedfishcloud.ext.sftp.config.SFTPProperty;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.AbstractRawDiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@Slf4j
public class SFTPDiskFileSystemFactory extends AbstractRawDiskFileSystemFactory<SFTPProperty, RawDiskFileSystem> {
    private static final DiskFileSystemDescribe DESCRIBE = DiskFileSystemDescribe.builder()
            .isPublic(true)
            .protocol("sftp")
            .name("SFTP文件传输")
            .describe("基于SSH的SFTP文件传输")
            .configNode(Collections.singletonList(ConfigNode.builder()
                    .title("基本信息")
                    .name("base")
                    .nodes(Arrays.asList(
                            ConfigNode.builder().name("host").inputType("text").required(true).build(),
                            ConfigNode.builder().name("port").inputType("text").required(true).build(),
                            ConfigNode.builder().name("username").inputType("text").required(true).build(),
                            ConfigNode.builder().name("path").inputType("text").required(true).build(),
                            ConfigNode.builder().name("password").inputType("text").isMask(true).build()
                    ))
                    .build()))
            .build();

    @Setter
    private ThumbnailService thumbnailService;

    @Override
    public SFTPProperty parseProperty(Map<String, Object> params) {
        return CollectionUtils.validMap(params)
                .addField("host")
                .addField("username")
                .addField("path")
                .validAndToBean(SFTPProperty.class);
    }

    @Override
    public RawDiskFileSystem generateDiskFileSystem(SFTPProperty property) {
        RawDiskFileSystem fileSystem = null;
        try {
            fileSystem = new RawDiskFileSystem(new SFTPDirectRawStoreHandler(property), property.getPath());
            fileSystem.setThumbnailService(thumbnailService);
            return fileSystem;
        } catch (IOException e) {
            log.error("获取文件系统失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }

}
