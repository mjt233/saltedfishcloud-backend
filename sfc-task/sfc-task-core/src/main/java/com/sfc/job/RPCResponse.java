package com.sfc.job;

import lombok.Data;

@Data
public class RPCResponse {
    /**
     * 是否被处理
     */
    private Boolean isHandled;

    /**
     * 处理结果
     */
    private Object result;
}
