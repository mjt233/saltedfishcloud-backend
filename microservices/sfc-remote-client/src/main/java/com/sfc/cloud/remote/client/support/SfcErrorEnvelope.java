package com.sfc.cloud.remote.client.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sfc.common.model.json.SfcJsonResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * core 响应信封解析工具。Feign 错误解码器与 RestClient 错误处理器共用，保证两条链路的错误语义一致。
 */
@Slf4j
final class SfcErrorEnvelope {

    /** 信封解析用的 ObjectMapper */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /** 工具类不允许实例化 */
    private SfcErrorEnvelope() {
    }

    /**
     * 解析 core 响应信封。
     *
     * @param body 响应体原文
     * @return 解析出的信封；响应体不是 core 信封时返回 null
     */
    static SfcJsonResult<Object> parse(String body) {
        if (body == null || body.isEmpty() || body.charAt(0) != '{') {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            SfcJsonResult<Object> envelope = OBJECT_MAPPER.readValue(body, SfcJsonResult.class);
            if (envelope.getCode() == 0 && envelope.getMsg() == null) {
                return null;
            }
            return envelope;
        } catch (IOException e) {
            log.debug("[sfc-remote] core 响应体不是标准信封：{}", e.toString());
            return null;
        }
    }
}
