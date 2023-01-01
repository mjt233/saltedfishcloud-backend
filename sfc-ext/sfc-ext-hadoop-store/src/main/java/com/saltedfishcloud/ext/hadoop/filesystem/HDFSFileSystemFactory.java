package com.saltedfishcloud.ext.hadoop.filesystem;

import com.saltedfishcloud.ext.hadoop.HDFSProperties;
import com.saltedfishcloud.ext.hadoop.HDFSUtils;
import com.saltedfishcloud.ext.hadoop.store.HDFSStoreHandler;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.*;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class HDFSFileSystemFactory extends AbstractRawDiskFileSystemFactory<HDFSProperties, RawDiskFileSystem> {
    @Setter
    private ThumbnailService thumbnailService;
    private static final DiskFileSystemDescribe DESCRIBE = DiskFileSystemDescribe.builder()
            .isPublic(true)
            .protocol("hdfs")
            .name("HDFS分布式存储")
            .describe("Hadoop的分布式文件系统存储支持")
            .configNode(Collections.singletonList(ConfigNode.builder()
                    .title("基本信息")
                    .name("base")
                    .nodes(Arrays.asList(
                            ConfigNode.builder().name("url").inputType("text").required(true).build(),
                            ConfigNode.builder().name("root").inputType("text").required(true).build(),
                            ConfigNode.builder().name("user").inputType("text").required(true).build()
                    ))
                    .build()))
            .build();


    @Override
    public HDFSProperties parseProperty(Map<String, Object> params) {

        CollectionUtils.validMap(params)
                .addField("url")
                .addField("root")
                .addField("user")
                .valid();
        return HDFSProperties.builder()
                .url(params.get("url").toString())
                .root(params.get("root").toString())
                .user(params.get("user").toString())
                .build();
    }

    @Override
    public RawDiskFileSystem generateDiskFileSystem(HDFSProperties property) throws IOException {

        RawDiskFileSystem hdfsFileSystem;
        FileSystem fileSystem = null;
        try {
            fileSystem = HDFSUtils.getFileSystem(property);
            HDFSStoreHandler storeHandler = new HDFSStoreHandler(fileSystem);
            hdfsFileSystem = new RawDiskFileSystem(storeHandler, property.getRoot());
            hdfsFileSystem.setThumbnailService(thumbnailService);
            return hdfsFileSystem;
        } catch (Exception e) {
            if (fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    throw new IOException("异常：", ex);
                }
            }
            throw new IOException("异常：" + e.getMessage(), e);
        }
    }


    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }

}
