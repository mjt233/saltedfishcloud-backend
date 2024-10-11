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
public class WebSocketRequest {
    /**
     * 请求动作
     *
     * @see WebSocketConstant.Action
     */
    private String action;

    /**
     * 数据业务类型
     * @see WebSocketConstant.Type
     */
    private String type;

    /**
     * 参数数据
     */
    private Object data;
}
