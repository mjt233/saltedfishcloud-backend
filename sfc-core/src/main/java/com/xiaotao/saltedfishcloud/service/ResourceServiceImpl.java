package com.xiaotao.saltedfishcloud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.helper.OutputStreamConsumer;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileDCInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.ResourceProtocolHandler;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {
    private final DiskFileSystemManager fileSystemFactory;
    private static final Map<String, String> DIRECT_DOWNLOAD_HEADER = new HashMap<>() {{
        put("Content-Type", FileUtils.getContentType("a.a"));
    }};

    private final Map<String, ResourceProtocolHandler> handlerMap = new HashMap<>();

    @Override
    public synchronized void addResourceHandler(ResourceProtocolHandler handler) {
        if(handlerMap.containsKey(handler.getProtocolName())) {
            throw new IllegalArgumentException("已存在针对协议" + handler.getProtocolName() + "的资源处理器");
        }
        handlerMap.put(handler.getProtocolName(), handler);
    }

    @Override
    public ResourceProtocolHandler getResourceHandler(String protocol) {
        return handlerMap.get(protocol);
    }

    @Override
    public Resource getResource(@Validated ResourceRequest param) throws UnsupportedProtocolException, IOException {
        ResourceProtocolHandler handler = handlerMap.get(param.getProtocol());
        if (handler == null) {
            throw new UnsupportedProtocolException(param.getProtocol());
        }
        return handler.getFileResource(param);
    }

    /**
     * 通过下载码获取资源响应体
     * @param dc 下载码
     * @return  资源响应体
     */
    @Override
    public ResponseEntity<Resource> getResourceByDownloadCode(String dc, boolean directDownload) throws IOException {
        FileDCInfo info;
        try {
            String data = JwtUtils.parse(dc);
            info = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false).readValue(data, FileDCInfo.class);
        } catch (JsonProcessingException e) {
            throw new JsonException(400, "下载码无效");
        }
        Resource resource = fileSystemFactory.getMainFileSystem().getResource(info.getUid(), info.getDir(), info.getName());
        if (directDownload) {
            return ResourceUtils.wrapResource(resource, info.getName(), DIRECT_DOWNLOAD_HEADER);
        } else {
            return ResourceUtils.wrapResource(resource, info.getName());
        }

    }

    /**
     * 获取网盘中文件的下载码
     * @param uid 用户ID
     * @param path 文件所在网盘目录
     * @param fileInfo 文件信息
     * @param expr  下载码有效时长（单位：天），若小于0，则无限制
     */
    @Override
    public String getFileDownloadCode(long uid, String path, FileInfo fileInfo, int expr) throws IOException {
        DiskFileSystem fileSystem = fileSystemFactory.getMainFileSystem();
        if (
                !fileSystem.exist(uid, PathBuilder.formatPath(path + "/" + fileInfo.getName(), true))
                        || fileSystem.getResource(uid, path, fileInfo.getName()) == null
        ) {
            throw new JsonException(404, "文件不存在");
        }
        FileDCInfo info = new FileDCInfo();
        info.setDir(path);
        info.setMd5(fileInfo.getMd5());
        info.setName(fileInfo.getName());
        info.setUid(uid);
        return JwtUtils.generateToken(MapperHolder.mapper.writeValueAsString(info), expr < 0 ? expr : expr*60*60*24);
    }

    @Override
    public void writeResource(ResourceRequest param, Resource resource) throws IOException {
        ResourceProtocolHandler handler = getResourceHandler(param.getProtocol());
        if (!handler.isWriteable()) {
            throw new IllegalArgumentException("目标资源不支持数据写入");
        }
        handler.writeResource(param, resource);
    }

    @Override
    public void writeResource(ResourceRequest param, OutputStreamConsumer<OutputStream> outputStream) throws IOException {
        ResourceProtocolHandler handler = getResourceHandler(param.getProtocol());
        if (!handler.isWriteable()) {
            throw new IllegalArgumentException("目标资源不支持数据写入");
        }
        handler.writeResource(param, outputStream);
    }
}
