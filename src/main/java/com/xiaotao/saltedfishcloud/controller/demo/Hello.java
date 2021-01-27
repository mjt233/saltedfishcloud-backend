package com.xiaotao.saltedfishcloud.controller.demo;

import com.xiaotao.saltedfishcloud.utils.JsonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class Hello {
    @GetMapping("/hello")
    public String hello(@RequestParam("a") Integer a
                        , @RequestParam("b") Integer b) {
        return a+b+"";
    }
}
