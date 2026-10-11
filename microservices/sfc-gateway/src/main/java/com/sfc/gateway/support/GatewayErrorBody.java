package com.sfc.gateway.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 网关自有错误体的<b>唯一构造点</b>。
 *
 * <p>
 * 对外错误契约由 core 定义（{@code {code, data, msg, businessCode}}），网关侧产出的错误
 * （{@code 503/7004}、框架层 413 的归一化 {@code 413/7003}、内部的 {@code 500}）必须与之同形——
 * 否则前端得为"网关错误"和"core 错误"写两套解析。
 * 需要它的地方有两处：兜底异常处理器与 resolve 客户端的失败分支，因此抽成一个组件而不是各写一遍。
 * </p>
 *
 * <p>
 * ⚠️ 序列化用的是 <b>Jackson 3</b>（{@code tools.jackson}）：Spring Boot 4 的自动配置提供的就是
 * {@code tools.jackson.databind.json.JsonMapper}，不再有 Jackson 2 的 {@code ObjectMapper} bean。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class GatewayErrorBody {

    /** JSON 序列化器（Boot 4 注入的是 Jackson 3 的 JsonMapper） */
    private final ObjectMapper objectMapper;

    /**
     * 构造与 core 同形的错误响应体。
     *
     * @param status       HTTP 状态码（写入 {@code code}）
     * @param businessCode 业务码；{@code 0} 表示该错误没有对应业务码
     * @param message      提示信息
     * @return JSON 字符串
     */
    public String json(int status, int businessCode, String message) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("code", status);
            node.putNull("data");
            node.put("msg", message);
            node.put("businessCode", businessCode);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            // 序列化理论上不会失败（全是标量），兜底也要保证"永远能回一个合法 JSON"
            return "{\"code\":" + status + ",\"data\":null,\"msg\":\"" + message + "\",\"businessCode\":"
                    + businessCode + "}";
        }
    }
}
