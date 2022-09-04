package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.WrapParam;
import com.xiaotao.saltedfishcloud.service.wrap.WrapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping(WrapController.PREFIX)
@Validated
public class WrapController {
    public static final String PREFIX = "/api/wrap";

    @Autowired
    private WrapService wrapService;

    /**
     * 创建打包任务
     * @return  打包信息
     */
    @PostMapping("create")
    public JsonResult createWrap(@RequestBody WrapParam param) {
        String wid = wrapService.registerWrap(param);
        return JsonResultImpl.getInstance(wid);
    }

    @GetMapping({ "{wid}", "{wid}/{alias}" })
    @AllowAnonymous
    public void wrapDownload(@PathVariable("wid") String wid,
                             @PathVariable(required = false, value = "alias") String alias,
                             HttpServletResponse response) throws IOException {
        wrapService.writeWrapToServlet(wid, alias, response);
    }
}
