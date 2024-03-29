package com.saltedfishcloud.ext.minio.filesystem;

import com.saltedfishcloud.ext.minio.MinioDirectRawHandler;
import com.saltedfishcloud.ext.minio.MinioProperties;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.*;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import io.minio.MinioClient;
import lombok.Setter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MinioFileSystemFactory extends AbstractRawDiskFileSystemFactory<MinioProperties, RawDiskFileSystem> {
    @Setter
    private ThumbnailService thumbnailService;

    private static final DiskFileSystemDescribe DESCRIBE = DiskFileSystemDescribe.builder()
            .isPublic(true)
            .protocol("minio")
            .name("Minio对象存储")
            .describe("Minio对象存储")
            .configNode(Collections.singletonList(ConfigNode.builder()
                            .title("基本信息")
                            .name("base")
                            .nodes(Arrays.asList(
                                    ConfigNode.builder().name("endpoint").inputType("text").required(true).build(),
                                    ConfigNode.builder().name("accessKey").inputType("text").required(true).build(),
                                    ConfigNode.builder().name("secretKey").inputType("text").required(true).build(),
                                    ConfigNode.builder().name("bucket").inputType("text").required(true).build()
                            ))
                    .build()))
            .build();
    private final Map<MinioProperties, DiskFileSystem> CACHE = new ConcurrentHashMap<>();


    @Override
    public MinioProperties parseProperty(Map<String, Object> params) {

        CollectionUtils.validMap(params)
                .addField("endpoint")
                .addField("accessKey")
                .addField("secretKey")
                .addField("bucket")
                .valid();
        return MinioProperties.builder()
                .endpoint(params.get("endpoint").toString())
                .accessKey(params.get("accessKey").toString())
                .secretKey(params.get("secretKey").toString())
                .bucket(params.get("bucket").toString())
                .build();
    }

    @Override
    public RawDiskFileSystem generateDiskFileSystem(MinioProperties property) throws IOException {
        MinioClient client = MinioClient.builder()
                .endpoint(property.getEndpoint())
                .credentials(property.getAccessKey(), property.getSecretKey())
                .build();
        MinioDirectRawHandler rawHandler = new MinioDirectRawHandler(client, property);
        RawDiskFileSystem rawDiskFileSystem = new RawDiskFileSystem(rawHandler, "/");
        rawDiskFileSystem.setThumbnailService(thumbnailService);
        return rawDiskFileSystem;
    }

    @Override
    public void testFileSystem(DiskFileSystem fileSystem) throws FileSystemParameterException {
        try {
            ((RawDiskFileSystem) fileSystem).getStoreHandler().listFiles("/");
        } catch (IOException e) {
            throw new FileSystemParameterException(e.getMessage(), e);
        }
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }


}
