package com.saltedfishcloud.ext.samba.filesystem;

import com.saltedfishcloud.ext.samba.SambaStorage;
import com.saltedfishcloud.ext.samba.SambaProperty;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.AbstractRawStorageFactory;
import com.xiaotao.saltedfishcloud.service.file.StorageMetadata;
import com.xiaotao.saltedfishcloud.service.file.store.ScopedStorage;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class SambaStorageFactory extends AbstractRawStorageFactory<SambaProperty, Storage> {
    /**
     * 兼容现有构造注入的缩略图服务。
     */
    private final ThumbnailService thumbnailService;

    private static final StorageMetadata DESCRIBE = StorageMetadata.builder()
            .isPublic(true)
            .protocol("samba")
            .name("Samba文件共享")
            .describe("Samba文件共享（实验性功能）")
            .configNode(Collections.singletonList(ConfigNode.builder()
                    .title("基本信息")
                    .name("base")
                    .nodes(Arrays.asList(
                            ConfigNode.builder().name("host").inputType("text").required(true).build(),
                            ConfigNode.builder().name("shareName").inputType("text").required(true).build(),
                            ConfigNode.builder().name("path").inputType("text").required(true).build(),
                            ConfigNode.builder().name("username").inputType("text").build(),
                            ConfigNode.builder().name("password").inputType("text").isMask(true).build()
                    ))
                    .build()))
            .build();

    @Override
    public SambaProperty parseProperty(Map<String, Object> params) {

        CollectionUtils.validMap(params)
                .addField("path")
                .addField("shareName")
                .addField("host")
                .valid();
        return SambaProperty.builder()
                .basePath(params.get("path").toString())
                .shareName(params.get("shareName").toString())
                .host(params.get("host").toString())
                .password(TypeUtils.toString(params.get("password")))
                .username(TypeUtils.toString(params.get("username")))
                .port(Integer.parseInt( Optional.ofNullable(params.get("port")).orElse("445").toString()))
                .build();
    }

    @Override
    public Storage generateStorage(SambaProperty property) throws IOException {
        return new ScopedStorage(new SambaStorage(property), property.getBasePath());
    }

    @Override
    public StorageMetadata getMetadata() {
        return DESCRIBE;
    }
}
