package com.sfc.rpc;

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

    /**
     * 忽略处理，不响应
     */
    public static <T> RPCResponse<T> ignore() {
        return RPCResponse.<T>builder()
                .isHandled(false)
                .build();
    }

    /**
     * 响应处理，但执行出错
     * @param message   错误消息
     */
    public static <T> RPCResponse<T> error(String message) {
        return RPCResponse.<T>builder()
                .isHandled(true)
                .isSuccess(false)
                .error(message)
                .result(null)
                .build();
    }

    /**
     * 响应处理，执行成功
     * @param result 响应内容
     */
    public static <T> RPCResponse<T> success(T result) {
        return RPCResponse.<T>builder()
                .isHandled(true)
                .isSuccess(true)
                .error(null)
                .result(result)
                .build();
    }

}
