package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;


@Controller
public class DownloadController {
    @javax.annotation.Resource
    FileService fileService;
    @GetMapping("/pubdown/**")
    public ResponseEntity<Resource> publicDownload(HttpServletRequest request) throws MalformedURLException, UnsupportedEncodingException {
        String srcPath = DiskConfig.PUBLIC_ROOT + "/" + URLUtils.getRequestFilePath("/pubdown", request);
        return fileService.sendFile(srcPath);
    }

    @GetMapping("/pridown/**")
    public ResponseEntity<Resource> privateDownload(HttpServletRequest request) throws MalformedURLException, UnsupportedEncodingException {
        String requestUrl = URLUtils.getRequestFilePath("/pridown", request);
        String srcPath = DiskConfig.PRIVATE_ROOT
                + "/"
                + SecurityContextHolder.getContext().getAuthentication().getName()
                + "/"
                + requestUrl;
        return fileService.sendFile(srcPath);
    }
}
