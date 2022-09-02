package com.xiaotao.saltedfishcloud.service.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.model.po.file.FileDCInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResourceService {
    private final DiskFileSystemProvider fileSystemFactory;
    private static final Map<String, String> DIRECT_DOWNLOAD_HEADER = new HashMap<String, String>() {{
       put("Content-Type", FileUtils.getContentType("a.a"));
    }};

    /**
     * 通过下载码获取资源响应体
     * @param dc 下载码
     * @return  资源响应体
     */
    public ResponseEntity<Resource> getResourceByDC(String dc, boolean directDownload) throws IOException {
        FileDCInfo info;
        try {
            String data = JwtUtils.parse(dc);
            info = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false).readValue(data, FileDCInfo.class);
        } catch (JsonProcessingException e) {
            throw new JsonException(400, "下载码无效");
        }
        Resource resource = fileSystemFactory.getFileSystem().getResource(info.getUid(), info.getDir(), info.getName());
        if (directDownload) {
            return ResourceUtils.wrapResource(resource, info.getName(), DIRECT_DOWNLOAD_HEADER);
        } else {
            return ResourceUtils.wrapResource(resource, info.getName());
        }

    }
}
