package com.xiaotao.saltedfishcloud.controller;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import emergency.EmergencyApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/api")
public class CommonController {

    @GetMapping("error")
    @AllowAnonymous
    public void test(HttpServletResponse response) throws IOException {
        response.setHeader("content-type", "text/plain;charset=utf-8");
        if(EmergencyApplication.throwable != null) {
            response.getWriter().println("异常出现时间：" + EmergencyApplication.errorDate);
            EmergencyApplication.throwable.printStackTrace(response.getWriter());
        }
    }
}
