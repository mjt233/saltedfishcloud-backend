package com.saltedfishcloud.ext.minio.filesystem;

import com.saltedfishcloud.ext.minio.MinioDirectRawHandler;
import com.saltedfishcloud.ext.minio.MinioProperties;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import io.minio.MinioClient;
import lombok.Setter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MinioFileSystemFactory implements DiskFileSystemFactory {
    @Setter
    private ThumbnailService thumbnailService;

    private static final DiskFileSystemDescribe DESCRIBE = DiskFileSystemDescribe.builder()
            .isPublic(true)
            .protocol("minio")
            .name("minio")
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
    public DiskFileSystem getFileSystem(Map<String, Object> params) throws FileSystemParameterException {
        MinioProperties minioProperties = checkAndGetProperties(params);
        return CACHE.computeIfAbsent(minioProperties, k -> generateFileSystem(minioProperties));
    }

    @Override
    public DiskFileSystem testGet(Map<String, Object> params) throws FileSystemParameterException {
        MinioProperties minioProperties = checkAndGetProperties(params);
        RawDiskFileSystem rawDiskFileSystem = generateFileSystem(minioProperties);
        try {
            rawDiskFileSystem.getStoreHandler().listFiles("/");
            return rawDiskFileSystem;
        } catch (IOException e) {
            throw new FileSystemParameterException(e.getMessage(), e);
        }
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }
    
    protected RawDiskFileSystem generateFileSystem(MinioProperties minioProperties) {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
        MinioDirectRawHandler rawHandler = new MinioDirectRawHandler(client, minioProperties);
        RawDiskFileSystem rawDiskFileSystem = new RawDiskFileSystem(rawHandler, "/");
        rawDiskFileSystem.setThumbnailService(thumbnailService);
        return rawDiskFileSystem;

    }

    public MinioProperties checkAndGetProperties(Map<String, Object> params) {
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
    };
}
