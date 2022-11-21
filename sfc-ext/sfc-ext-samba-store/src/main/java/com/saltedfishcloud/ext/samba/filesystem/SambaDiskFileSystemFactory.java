package com.saltedfishcloud.ext.samba.filesystem;

import com.saltedfishcloud.ext.samba.SambaDirectRawStoreHandler;
import com.saltedfishcloud.ext.samba.SambaProperty;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemDescribe;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SambaDiskFileSystemFactory implements DiskFileSystemFactory {
    @Autowired
    private ThumbnailService thumbnailService;
    private final Map<SambaProperty, SambaDiskFileSystem> CACHE = new ConcurrentHashMap<>();
    private static final DiskFileSystemDescribe DESCRIBE = DiskFileSystemDescribe.builder()
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

    private SambaProperty parseProperty(Map<String, Object> params) {

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

    protected SambaDiskFileSystem generateDiskFileSystem(SambaProperty property) {
        SambaDirectRawStoreHandler handler = new SambaDirectRawStoreHandler(property);
        SambaDiskFileSystem fileSystem = new SambaDiskFileSystem(handler, property.getBasePath());
        fileSystem.setThumbnailService(thumbnailService);
        return fileSystem;
    }

    @Override
    public DiskFileSystem getFileSystem(Map<String, Object> params) throws FileSystemParameterException {
        SambaProperty sambaProperty = parseProperty(params);
        return CACHE.computeIfAbsent(sambaProperty, key -> generateDiskFileSystem(sambaProperty));
    }

    @Override
    public void testFileSystem(DiskFileSystem fileSystem) throws FileSystemParameterException {

        boolean exist = fileSystem.exist(0, "/");
        if (!exist) {
            throw new FileSystemParameterException("路径不存在");
        }
    }

    @Override
    public void clearCache(Collection<Map<String, Object>> params) {
        Set<SambaProperty> paramSet = params.stream().map(this::parseProperty).collect(Collectors.toSet());
        for (Map.Entry<SambaProperty, SambaDiskFileSystem> entry : CACHE.entrySet()) {
            try {
                if (!paramSet.contains(entry.getKey())) {
                    entry.getValue().close();
                    CACHE.remove(entry.getKey());
                }
            } catch (IOException err) {
                err.printStackTrace();
            }
        }

    }

    @Override
    public DiskFileSystemDescribe getDescribe() {
        return DESCRIBE;
    }
}
