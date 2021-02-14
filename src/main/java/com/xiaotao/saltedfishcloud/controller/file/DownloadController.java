package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;


@Controller
@Slf4j
public class DownloadController {
    @javax.annotation.Resource
    FileService fileService;

    @GetMapping("/download/{uid}/**")
    public ResponseEntity<Resource> download(HttpServletRequest request,
                                             @PathVariable int uid)
            throws MalformedURLException, UnsupportedEncodingException {

        // 解析URL
        UIDValidator.validate(uid);
        String prefix = "/download/" + uid;
        String requestPath = URLUtils.getRequestFilePath(prefix, request);

        String srcPath = FileUtils.getFileStoreRootPath(uid) + "/" + requestPath;
        return fileService.sendFile(srcPath);
    }
}
