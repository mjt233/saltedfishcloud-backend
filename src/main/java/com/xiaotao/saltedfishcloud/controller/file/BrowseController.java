package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.FileService;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

@Controller
public class BrowseController {

    @Resource
    FileService fileService;
    @GetMapping("/public/**")
    public String publicList(Model model, HttpServletRequest request, HttpServletResponse response) {
        String uri = null;
        List<FileInfo>[] fileList = null;
        String path = URLUtils.getRequestFilePath("/public", request);
        String srcPath = DiskConfig.PUBLIC_ROOT + "/" + path;
        model.addAttribute("uri", path);
        try {
            fileList = fileService.getFileList(srcPath);
            model.addAttribute("files", fileList[1]);
            model.addAttribute("dirs", fileList[0]);
        } catch (FileNotFoundException e) {
            response.setStatus(404);
            return "error/404";
        } catch (NullPointerException e) {
            model.addAttribute("file",new FileInfo(new File(srcPath)));
            return "fileDownload";
        }
        return "filelist";
    }

    @GetMapping("/api/getPublicList/**")
    @ResponseBody
    public JsonResult getPublicList(HttpServletRequest request) throws HasResultException {
        String path = URLUtils.getRequestFilePath("/api/getPublicList", request);
        List<FileInfo>[] fileList = null;
        try {
            fileList = fileService.getFileList(DiskConfig.PUBLIC_ROOT + "/" + path);
        } catch (FileNotFoundException e) {
            throw new HasResultException(404, "路径不存在");
        }
        return JsonResult.getInstance(fileList);
    }

    @GetMapping("/api/getPrivateList/**")
    @ResponseBody
    public JsonResult getPrivateList(HttpServletRequest request,
                                     @RequestAttribute User user) throws FileNotFoundException {
        String requestFilePath = URLUtils.getRequestFilePath("/api/getPrivateList", request);
        String userBasePath = DiskConfig.PRIVATE_ROOT + "/" + user.getUser();
        File file = new File(userBasePath);
        if (!file.exists()) {
            file.mkdir();
        }
        List<FileInfo>[] fileList = fileService.getFileList(userBasePath + requestFilePath);
        return JsonResult.getInstance(fileList);
    }

}
