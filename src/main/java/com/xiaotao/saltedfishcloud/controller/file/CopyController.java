package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.utils.UIDValidator;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
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
     * @param uid    用户ID
     * @param name   文件名
     * @param target 目标目录
     * @param overwrite 覆盖同名文件
     * @TODO 使用数组传入需要复制的文件名以替代并发请求接口实现多文件粘贴的方式
     */
    @PostMapping("/copy/{uid}/**")
    public JsonResult copy(@PathVariable("uid") int uid,
                           @RequestParam("name") String name,
                           @RequestParam("target") String target,
                           @RequestParam(value = "overwrite", defaultValue = "true") Boolean overwrite,
                           HttpServletRequest request) throws IOException {
        UIDValidator.validate(uid);
        String source = URLUtils.getRequestFilePath("/api/copy/" + uid, request);
        fileService.copy(uid, source, target, uid, name, overwrite);
        return JsonResult.getInstance();
    }
}
