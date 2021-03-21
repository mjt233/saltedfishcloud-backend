package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;


/**
 * 下载功能相关的路由器
 */
@Controller
@Slf4j
public class DownloadController {
    @javax.annotation.Resource
    FileService fileService;

    @RequestMapping(value = "/download/{uid}/**", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Resource> download(HttpServletRequest request,
                                             @PathVariable int uid)
            throws MalformedURLException, UnsupportedEncodingException {

        // 解析URL
        UIDValidator.validate(uid);
        String prefix = "/download/" + uid;
        String requestPath = URLUtils.getRequestFilePath(prefix, request);

        String srcPath = DiskConfig.getRawFileStoreRootPath(uid) + "/" + requestPath;
        return fileService.sendFile(srcPath);
    }

    @GetMapping(value = "/api/fdc/{code}")
    @ResponseBody
    public ResponseEntity<Resource> downloadUseDC(@PathVariable String code) throws UnsupportedEncodingException, MalformedURLException {
        return fileService.getResourceByDC(code);
    }
}
