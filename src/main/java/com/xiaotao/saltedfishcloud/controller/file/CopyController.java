package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.param.FileCopyOrMoveInfo;
import com.xiaotao.saltedfishcloud.po.param.NamePair;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/api")
public class CopyController {

    @Resource
    private FileService fileService;

    /**
     * 复制文件或目录到指定目录下
     * @TODO 使用数组传入需要复制的文件名以替代并发请求接口实现多文件粘贴的方式
     */
    @PostMapping("/copy/{uid}/**")
    public JsonResult copy( @PathVariable("uid") int uid,
                            @RequestBody @Validated FileCopyOrMoveInfo info,
                            HttpServletRequest request) throws IOException {
        UIDValidator.validate(uid);
        String source = URLUtils.getRequestFilePath("/api/copy/" + uid, request);
        boolean overwrite = info.isOverwrite();
        String target = info.getTarget();
        for (NamePair file : info.getFiles()) {
            fileService.copy(uid, source, target, uid, file.getSource(), file.getTarget(), overwrite);
        }
        return JsonResult.getInstance();
    }
}
