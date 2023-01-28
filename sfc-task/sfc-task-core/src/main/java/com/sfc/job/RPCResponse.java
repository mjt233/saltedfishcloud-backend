package com.sfc.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RPCResponse<T> {
    /**
     * 是否被处理
     */
    private Boolean isHandled;

    /**
     * 是否调用成功
     */
    private Boolean isSuccess;

    /**
     * 错误消息
     */
    private String error;

    /**
     * 处理结果
     */
    private T result;
}
