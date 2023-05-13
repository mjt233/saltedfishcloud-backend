package com.xiaotao.saltedfishcloud.model.websocket;

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
     * @see com.sfc.constant.WebSocketConstant.Action
     */
    private String action;

    /**
     * 数据业务类型
     * @see com.sfc.constant.WebSocketConstant.Type
     */
    private String type;

    /**
     * 参数数据
     */
    private Object data;
}
