package com.xiaotao.saltedfishcloud.service.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.entity.po.file.FileDCInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class ResourceService {
    private final DiskFileSystemFactory fileSystemFactory;

    /**
     * 通过下载码获取资源响应体
     * @param dc 下载码
     * @return  资源响应体
     */
    public ResponseEntity<Resource> getResourceByDC(String dc, boolean directDownload) throws MalformedURLException, UnsupportedEncodingException {
        FileDCInfo info;
        try {
            String data = JwtUtils.parse(dc);
            info = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false).readValue(data, FileDCInfo.class);
        } catch (JsonProcessingException e) {
            throw new JsonException(400, "下载码无效");
        }
        var resource = fileSystemFactory.getFileSystem().getResource(info.getUid(), info.getDir(), info.getName());
        return ResourceUtils.wrapResource(resource, info.getName());
    }
}
