package com.xiaotao.saltedfishcloud.controller.file;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * 移动/重命名资源控制器
 */
@RestController
@RequestMapping("/api")
public class MoveController {
    @Resource
    private FileService fileService;

    @PostMapping("/rename/{uid}/**")
    @Transactional(rollbackFor = Exception.class)
    public JsonResult rename(HttpServletRequest request,
                           @PathVariable int uid,
                           @RequestParam("oldName") String oldName,
                           @RequestParam("newName") String newName) throws HasResultException {
        UIDValidator.validate(uid, true);
        String from = URLUtils.getRequestFilePath("/api/rename/" + uid, request);
        if (newName.length() < 1) {
            throw new HasResultException(400, "文件名不能为空");
        }
        fileService.rename(uid, from, oldName, newName);
        return JsonResult.getInstance();
    }

    /**
     * 移动文件或目录到指定目录下
     * @param uid    用户ID
     * @param name   文件名
     * @param target 目标目录
     */
    @PostMapping("/move/{uid}/**")
    public JsonResult move(HttpServletRequest request,
                            @PathVariable("uid") int uid,
                            @RequestParam("name") String name,
                            @RequestParam("target") String target) throws UnsupportedEncodingException {
        UIDValidator.validate(uid);
        String source = URLUtils.getRequestFilePath("/api/move/" + uid, request);
        target = URLDecoder.decode(target, "UTF-8");
        if (source.equals(target)) {
            throw new HasResultException(400, "不能原处移动");
        }
        fileService.move(uid, source, target, name);
        return JsonResult.getInstance(source);
    }
}
