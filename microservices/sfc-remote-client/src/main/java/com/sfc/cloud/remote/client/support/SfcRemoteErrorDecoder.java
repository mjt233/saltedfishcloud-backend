package com.sfc.cloud.remote.client.support;

import com.sfc.cloud.exception.SfcRemoteApiException;
import com.sfc.common.model.json.SfcJsonResult;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * core 错误信封解码器（Feign）。
 */
@Slf4j
public class SfcRemoteErrorDecoder implements ErrorDecoder {

    /** 兜底解码器 */
    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String rawBody = readBody(response);
        SfcJsonResult<Object> envelope = SfcErrorEnvelope.parse(rawBody);
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

    /** 用已读取的响应体重建响应 */
    private Response rebuild(Response response, String rawBody) {
        return response.toBuilder()
                .body(rawBody, StandardCharsets.UTF_8)
                .build();
    }
}
