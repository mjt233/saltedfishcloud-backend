package com.sfc.cloud.remote.client.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sfc.cloud.remote.SfcRemoteApiException;
import com.sfc.cloud.remote.model.SfcJsonResult;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * core 错误信封解码器。
 */
@Slf4j
public class SfcRemoteErrorDecoder implements ErrorDecoder {

    /** 兜底解码器 */
    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    /** 信封解析用的 ObjectMapper */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    @Override
    public Exception decode(String methodKey, Response response) {
        String rawBody = readBody(response);
        SfcJsonResult<Object> envelope = parseEnvelope(rawBody);
        if (envelope != null) {
            log.debug("[sfc-remote] core 返回错误：method={}, http={}, code={}, msg={}",
                    methodKey, response.status(), envelope.getCode(), envelope.getMsg());
            return new SfcRemoteApiException(response.status(), envelope.getCode(), envelope.getBusinessCode(),
                    methodKey, envelope.getMsg());
        }
        if (!rawBody.isEmpty()) {
            return defaultDecoder.decode(methodKey, rebuild(response, rawBody));
        }
        return defaultDecoder.decode(methodKey, response);
    }

    /** 读取响应体 */
    private String readBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try {
            return Util.toString(response.body().asReader(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("[sfc-remote] 读取 core 错误响应体失败：{}", e.toString());
            return "";
        }
    }

    /** 解析 core 响应信封 */
    private SfcJsonResult<Object> parseEnvelope(String body) {
        if (body.isEmpty() || body.charAt(0) != '{') {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            SfcJsonResult<Object> envelope = objectMapper.readValue(body, SfcJsonResult.class);
            if (envelope.getCode() == 0 && envelope.getMsg() == null) {
                return null;
            }
            return envelope;
        } catch (IOException e) {
            log.debug("[sfc-remote] core 错误响应体不是标准信封：{}", e.toString());
            return null;
        }
    }

    /** 用已读取的响应体重建响应 */
    private Response rebuild(Response response, String rawBody) {
        return response.toBuilder()
                .body(rawBody, StandardCharsets.UTF_8)
                .build();
    }
}
