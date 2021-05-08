package com.xiaotao.saltedfishcloud.controller.file;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.param.FileCopyOrMoveInfo;
import com.xiaotao.saltedfishcloud.po.param.NamePair;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;

import com.xiaotao.saltedfishcloud.validator.custom.FileName;
import com.xiaotao.saltedfishcloud.validator.custom.ValidPath;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * 移动/重命名资源控制器
 */
@RestController
@RequestMapping("/api")
@Validated
public class MoveController {
    @Resource
    private FileService fileService;

    @PostMapping("/rename/{uid}/**")
    @Transactional(rollbackFor = Exception.class)
    public JsonResult rename(HttpServletRequest request,
                           @PathVariable int uid,
                           @RequestParam("oldName") @Valid @FileName String oldName,
                           @RequestParam("newName") @Valid @FileName String newName) throws HasResultException, NoSuchFileException {
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
     */
    @PostMapping("/move/{uid}/**")
    public JsonResult move(HttpServletRequest request,
                            @PathVariable("uid") int uid,
                            @RequestBody @Valid FileCopyOrMoveInfo info)
            throws UnsupportedEncodingException, NoSuchFileException {
        UIDValidator.validate(uid);
        String source = URLUtils.getRequestFilePath("/api/move/" + uid, request);
        String target = URLDecoder.decode(info.getTarget(), "UTF-8");
        for (NamePair file : info.getFiles()) {
            fileService.move(uid, source, target, file.getSource(), info.isOverwrite());
        }
        return JsonResult.getInstance();
    }
}
