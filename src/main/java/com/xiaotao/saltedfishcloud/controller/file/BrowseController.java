package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;

@Controller
public class BrowseController {

    @Resource
    FileService fileService;

    @GetMapping("/api/fileList/{uid}/**")
    @ResponseBody
    public JsonResult getFileList(HttpServletRequest request,
                                  @PathVariable int uid) {
        // 解析URL
        UIDValidator.validate(uid);
        String prefix = "/api/fileList/" + uid;
        String requestPath = URLUtils.getRequestFilePath(prefix, request);

        String baseLocalPath = FileUtils.getFileStoreRootPath(uid);

        Collection<? extends FileInfo>[] fileList = null;
        File root = new File(baseLocalPath);
        if (!root.exists()) {
            root.mkdir();
        }
        try {
            fileList = fileService.getFileList(baseLocalPath + "/" + requestPath);
        } catch (FileNotFoundException e) {
            throw new HasResultException(404, "路径不存在");
        }
        return JsonResult.getInstance(fileList);

    }

}
