package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.apache.catalina.security.SecurityUtil;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    @GetMapping("/test")
    public JsonResult test() {
        System.out.println("测试控制器:" + SecureUtils.getSpringSecurityUser());
        return JsonResult.getInstance();
    }
}
