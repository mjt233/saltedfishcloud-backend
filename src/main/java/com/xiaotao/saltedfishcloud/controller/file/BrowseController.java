package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.Collection;

/**
 * 浏览控制器，提供浏览功能
 */
@Controller
public class BrowseController {

    @Resource
    FileService fileService;

    /**
     * 取网盘中某个目录的文件列表
     * @param uid   目标用户资源的ID
     * @TODO 统一文件资源操作getList,copy,delete,download,mkdir,move,upload的API路由前缀
     */
    @GetMapping("/api/fileList/{uid}/**")
    @ResponseBody
    public JsonResult getFileList(HttpServletRequest request,
                                  @PathVariable int uid) throws NoSuchFileException {
        // 解析URL
        UIDValidator.validate(uid);
        String prefix = "/api/fileList/" + uid;
        String requestPath = URLUtils.getRequestFilePath(prefix, request);

        String baseLocalPath = DiskConfig.getRawFileStoreRootPath(uid);

        Collection<? extends FileInfo>[] fileList;
        File root = new File(baseLocalPath);
        if (!root.exists()) {
            root.mkdir();
        }
        fileList = fileService.getUserFileList(uid, requestPath);
        return JsonResult.getInstance(fileList);

    }

}
