package com.sfc.cloud.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一的 JSON 错误响应写出工具。
 * <p>
 * 响应体形态刻意与 core 侧的 {@code JsonResultImpl} 保持一致（{@code code} / {@code data} / {@code msg}），
 * 让调用方不必区分"core 返回的错误"与"微服务返回的错误"。
 * </p>
 * <p>
 * 本库不依赖咸鱼云任何模块，因此这里自己用 Jackson 拼装，而不是复用 core 的响应包装类。
 * </p>
 */
final class ErrorResponses {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorResponses() {
    }

    /**
     * 写出 JSON 错误响应。
     *
     * @param response HTTP 响应
     * @param status   HTTP 状态码
     * @param message  提示信息
     * @throws IOException 写出失败时抛出
     */
    static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(status);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status);
        body.put("data", null);
        body.put("msg", message);
        MAPPER.writeValue(response.getWriter(), body);
    }
}
