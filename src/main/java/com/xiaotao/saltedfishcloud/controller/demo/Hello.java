package com.xiaotao.saltedfishcloud.controller.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Hello {
    @GetMapping("/hello")
    public String hello(@RequestParam("a") Integer a
                        , @RequestParam("b") Integer b) {
        return a+b+"";
    }
}
