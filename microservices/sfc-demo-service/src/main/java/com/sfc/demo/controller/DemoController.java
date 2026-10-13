package com.sfc.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微服务示例控制器
 */
@RestController
@RequestMapping(DemoController.PREFIX)
public class DemoController {
    public static final String PREFIX = "/api/demo";

    /** 网关注入的用户身份头（框架期信任模式下的唯一身份来源） */
    public static final String HEADER_UID = "X-Sfc-Uid";

    /**
     * 回显当前用户。
     *
     * @param request HTTP 请求
     * @return 当前 uid 及其来源说明
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoami(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        String rawUid = request.getHeader(HEADER_UID);
        result.put("uid", rawUid);
        result.put("uidSource", HEADER_UID);
        result.put("rawUidHeader", rawUid);
        return result;
    }
}
