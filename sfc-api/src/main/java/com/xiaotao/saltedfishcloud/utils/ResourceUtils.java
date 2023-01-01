package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.common.ResponseResource;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ResourceUtils {
    public static class Header {
        public static final String ContentDisposition = "Content-Disposition";
    }
    // 默认的一年缓存响应
    private static final Map<String, String> DEFAULT_CACHE_MAP = new HashMap<String, String>(){{
        put("Cache-Control", "max-age=" + 60*60*24*7);
    }};

    /**
     * 对一个Resource包装为HTTP响应
     * @param resource 被包装的Resource
     */
    public static ResponseEntity<Resource> wrapResource(Resource resource) throws UnsupportedEncodingException {
        return ResourceUtils.wrapResource(resource, resource.getFilename(), null);
    }

    /**
     * 将资源包装为HTTP响应
     * @param resource  资源
     * @param filename  文件名
     * @param extraHeaders  额外的响应头
     */
    public static ResponseEntity<Resource> wrapResource(Resource resource, String filename, Map<String, String> extraHeaders) throws UnsupportedEncodingException {
        String ct = null;
        if (resource instanceof ResponseResource) {
            ct = ((ResponseResource) resource).getContentType();
        }
        String disposition = generateContentDisposition(filename);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("Content-Type", Optional.ofNullable(ct).orElseGet(() -> FileUtils.getContentType(filename)))
                .header("Content-Disposition", disposition);
        if (extraHeaders != null) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                builder.header(e.getKey(), e.getValue());
            }
        }
        return builder.body(resource);
    }

    /**
     * 对一个Resource包装为HTTP响应
     * @param resource 被包装的Resource
     * @param name     响应文件名
     */
    public static ResponseEntity<Resource> wrapResource(Resource resource, String name) throws UnsupportedEncodingException {
        return wrapResource(resource, name, null);
    }


    /**
     * 对一个Resource包装为HTTP响应，并让浏览器缓存其1年
     * @param resource  被包装的Resource
     * @param name      响应文件名
     */
    public static ResponseEntity<Resource> wrapResourceWithCache(Resource resource, String name) throws UnsupportedEncodingException {
        return wrapResource(resource, name, DEFAULT_CACHE_MAP);
    }


    public static String generateContentDisposition(String filename) throws UnsupportedEncodingException {
        return "inline;filename*=UTF-8''"+ URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    /**
     * 生成一个字符串资源
     * @param string    要封装的字符串
     */
    public static ResponseResource stringToResource(String string) {
        return new ResponseResource() {
            @Override
            public String getDescription() {
                return "StringResource";
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
