package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.utils.JsonResult;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {
    @RequestMapping("/api/userLogin")
    @ResponseBody
    public JsonResult login() {
        return JsonResult.getInstance(-1, null, "未登录");
    }
}
