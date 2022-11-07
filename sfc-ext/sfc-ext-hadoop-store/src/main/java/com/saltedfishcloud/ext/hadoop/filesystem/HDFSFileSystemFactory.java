package com.saltedfishcloud.ext.hadoop.filesystem;

import com.saltedfishcloud.ext.hadoop.HDFSProperties;
import com.saltedfishcloud.ext.hadoop.HDFSUtils;
import com.saltedfishcloud.ext.hadoop.store.HDFSStoreHandler;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
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
public class HDFSFileSystemFactory implements DiskFileSystemFactory {
    @Setter
    private ThumbnailService thumbnailService;

    private final Map<HDFSProperties, HDFSFileSystem> cache = new ConcurrentHashMap<>();

    @Override
    public void clearCache(Collection<Map<String, Object>> params) {
        Set<HDFSProperties> collect = params.stream().map(this::checkAndGetProperties).collect(Collectors.toSet());
        for (Map.Entry<HDFSProperties, HDFSFileSystem> entry : cache.entrySet()) {
            try {
                if(!collect.contains(entry.getKey())) {
                    entry.getValue().close();
                    cache.remove(entry.getKey());
                }
            } catch (IOException e) {
                log.error("[HDFS分布式存储]关闭文件系统异常：" + entry.getKey(), e);
            }
        }
    }

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

    /**
     * 获取所有缓存的文件系统。
     * 由外部进行判断缓存是否可以失效并执行close。
     * todo 定时任务定时清理失效的缓存
     */
    public Map<HDFSProperties, HDFSFileSystem> getAllCache() {
        return cache;
    }

    @Override
    public DiskFileSystem getFileSystem(Map<String, Object> params) throws FileSystemParameterException {
        HDFSProperties properties = checkAndGetProperties(params);
        HDFSFileSystem hdfsFileSystem = cache.get(properties);
        if (hdfsFileSystem != null) {
            return hdfsFileSystem;
        }

        FileSystem fileSystem = null;
        try {
            fileSystem = HDFSUtils.getFileSystem(properties);
            HDFSStoreHandler storeHandler = new HDFSStoreHandler(fileSystem);
            hdfsFileSystem = new HDFSFileSystem(storeHandler, properties.getRoot(), fileSystem);
            hdfsFileSystem.setThumbnailService(thumbnailService);
            cache.put(properties, hdfsFileSystem);
            return hdfsFileSystem;
        } catch (Exception e) {
            if (fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    throw new FileSystemParameterException("异常：", ex);
                }
            }
            throw new FileSystemParameterException("异常：" + e.getMessage(), e);
        }
    }

    @Override
    public DiskFileSystem testGet(Map<String, Object> params) throws FileSystemParameterException {
        return getFileSystem(params);
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }



    public HDFSProperties checkAndGetProperties(Map<String, Object> params) {
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
    };
}
