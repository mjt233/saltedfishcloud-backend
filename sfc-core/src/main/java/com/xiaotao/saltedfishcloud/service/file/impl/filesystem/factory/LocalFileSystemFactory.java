package com.xiaotao.saltedfishcloud.service.file.impl.filesystem.factory;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.*;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalDirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocalFileSystemFactory extends AbstractRawDiskFileSystemFactory<String, RawDiskFileSystem> implements InitializingBean {
    private static final List<ConfigNode> CONFIG_NODE_LIST = new ArrayList<>();
    private static final DiskFileSystemDescribe DESCRIBE;
    static {
        CONFIG_NODE_LIST.add(ConfigNode.builder()
                        .name("基本信息")
                        .nodes(Collections.singletonList(
                                ConfigNode.builder()
                                        .name("path")
                                        .title("要挂载的路径")
                                        .describe("选择咸鱼云所在的服务器的本地文件系统目录")
                                        .defaultValue("/")
                                        .required(true)
                                        .inputType("text")
                                        .build()
                        ))
                .build());
        DESCRIBE = DiskFileSystemDescribe.builder()
                .configNode(CONFIG_NODE_LIST)
                .name("本地文件系统")
                .describe("访问本地文件系统")
                .protocol(ResourceProtocol.LOCAL)
                .isPublic(false)
                .build();
    }

    private final Map<String, DiskFileSystem> CACHE = new ConcurrentHashMap<>();

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private ThumbnailService thumbnailService;

    private void checkParams(Map<String, Object> params) {
        if (!params.containsKey("path")) {
            throw new IllegalArgumentException("缺少参数path");
        }
    }


    @Override
    public String parseProperty(Map<String, Object> params) {
        checkParams(params);
        return params.get("path").toString();
    }

    @Override
    public RawDiskFileSystem generateDiskFileSystem(String path) throws IOException {
        LocalDirectRawStoreHandler handler = new LocalDirectRawStoreHandler();
        RawDiskFileSystem rawDiskFileSystem = new RawDiskFileSystem(handler, path);
        rawDiskFileSystem.setThumbnailService(thumbnailService);
        return rawDiskFileSystem;
    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        diskFileSystemManager.registerFileSystem(this);
    }
}
