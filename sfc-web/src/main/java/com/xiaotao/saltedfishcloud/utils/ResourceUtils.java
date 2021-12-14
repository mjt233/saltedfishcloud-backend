package com.xiaotao.saltedfishcloud.utils;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class ResourceUtils {
    public static class Header {
        public static final String ContentDisposition = "Content-Disposition";
    }
    public static ResponseEntity<Resource> wrapResource(Resource resource) throws UnsupportedEncodingException {
        String name = resource.getFilename();
        assert name != null;
        String disposition = "inline;filename*=UTF-8''"+ URLEncoder.encode(name, "utf-8");
        String ct = FileUtils.getContentType(name);
        return ResponseEntity.ok()
                .header("Content-Type", ct)
                .header("Content-Disposition", disposition)
                .body(resource);
    }

    public static String generateContentDisposition(String filename) throws UnsupportedEncodingException {
        return "inline;filename*=UTF-8''"+ URLEncoder.encode(filename, "utf-8");
    }
}
