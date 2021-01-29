package com.xiaotao.saltedfishcloud.controller.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.utils.JsonResult;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class MoveController {

    @PostMapping("/move/private/**")
    public JsonResult move(HttpServletRequest request,
                           @RequestParam("to") String to,
                           @RequestParam("from") String from) {
        String requestPath = URLUtils.getRequestFilePath("/api/private/move", request);
        return JsonResult.getInstance(requestPath);
    }
}
