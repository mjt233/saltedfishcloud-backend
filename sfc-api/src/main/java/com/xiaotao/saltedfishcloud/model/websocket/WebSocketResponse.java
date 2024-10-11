package com.xiaotao.saltedfishcloud.model.websocket;

import com.xiaotao.saltedfishcloud.constant.WebSocketConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WebSocketResponse {
    /**
     * 业务类型
     *
     * @see WebSocketConstant.Type
     */
    private String type;

    /**
     * 业务数据id
     */
    private Object id;

    /**
     * 负载数据
     */
    private Object data;
}
