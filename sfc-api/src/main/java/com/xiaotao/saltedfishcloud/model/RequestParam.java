package com.xiaotao.saltedfishcloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class RequestParam {
    /**
     * 请求接口
     */
    private String url;

    /**
     * 请求方法
     */
    private HttpMethod method;

    /**
     * 请求体
     */
    private Object body;

    /**
     * 附加的请求头
     */
    private Map<String, List<String>> headers;

    /**
     * 在QueryString上拼接的参数
     */
    private Map<String, String> parameters;

    public RequestParam addParameters(String key, String value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(key, value);
        return this;
    }

    public RequestParam addHeader(String key, String value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.computeIfAbsent(key, k -> new ArrayList<>())
                .add(value);
        return this;
    }
}
