package com.xiaotao.saltedfishcloud.service.file.impl.filesystem.factory;

import com.xiaotao.saltedfishcloud.constant.ResourceProtocol;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.AbstractStorageFactory;
import com.xiaotao.saltedfishcloud.service.file.StorageMetadata;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStorage;
import com.xiaotao.saltedfishcloud.service.file.store.ScopedStorage;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class LocalStorageFactory extends AbstractStorageFactory<String, Storage> {
    private static final List<ConfigNode> CONFIG_NODE_LIST = new ArrayList<>();
    private static final StorageMetadata DESCRIBE;
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
        DESCRIBE = StorageMetadata.builder()
                .configNode(CONFIG_NODE_LIST)
                .name("本地文件系统")
                .describe("访问本地文件系统")
                .protocol(ResourceProtocol.LOCAL)
                .isPublic(false)
                .build();
    }

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
    public Storage generateStorage(String path) throws IOException {
        return new ScopedStorage(new LocalStorage(), path);
    }

    @Override
    public StorageMetadata getMetadata() {
        return DESCRIBE;
    }
}
