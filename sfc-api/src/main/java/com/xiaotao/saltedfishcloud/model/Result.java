package com.xiaotao.saltedfishcloud.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Result<T, P> {
    private boolean isSuccess;

    private T data;

    private P param;

    private String message;

    private Exception exception;

    public static <T, P> Result<T, P> success(T data, String message) {
        return Result.<T, P>builder()
                .isSuccess(true)
                .data(data)
                .message(message)
                .build();
    }

    public static <T, P> Result<T, P> success(T data) {
        return Result.<T, P>builder()
                .isSuccess(true)
                .data(data)
                .message(null)
                .build();
    }

    public static <T, P> Result<T, P> success() {
        return Result.<T, P>builder()
                .isSuccess(true)
                .data(null)
                .message(null)
                .build();
    }
}
