package com.sfc.demo.controller;

import com.sfc.cloud.trust.SfcTrustHeaders;
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

    /**
     * 回显当前用户。
     *
     * @param request HTTP 请求
     * @return 当前 uid 及其来源说明
     */
    @GetMapping("/whoami")
    public Map<String, Object> whoami(HttpServletRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        String rawUid = request.getHeader(SfcTrustHeaders.UID);
        result.put("uid", rawUid);
        result.put("uidSource", SfcTrustHeaders.UID);
        result.put("rawUidHeader", rawUid);
        return result;
    }
}
