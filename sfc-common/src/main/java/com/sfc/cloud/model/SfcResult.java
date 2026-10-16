package com.sfc.cloud.model;

import lombok.Builder;
import lombok.Data;

/**
 * 通用业务结果模型，同时承载「业务数据」与「本次请求的入参」两段内容。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.model.Result} 逐字迁移而来（仅包名/类名变更），
 * 字段名、静态工厂与 Lombok 注解均保持一致，因此序列化结构零变化。</p>
 *
 * <p><b>关于 {@code isSuccess} 字段：</b>字段名以 {@code is} 开头，Lombok 会据此生成
 * {@code isSuccess()} / {@code setSuccess(boolean)} 以及建造者方法 {@code isSuccess(boolean)}。
 * 该命名是宿主既有契约，<b>不可改名</b>，否则调用点与 JSON 字段名都会漂移。</p>
 *
 * @param <T> 业务数据类型
 * @param <P> 请求参数类型（不需要回带入参时可为任意类型）
 */
@Data
@Builder
public class SfcResult<T, P> {
    /** 是否处理成功 */
    private boolean isSuccess;

    /** 业务数据 */
    private T data;

    /** 本次请求的入参（用于把请求参数回带给调用方） */
    private P param;

    /** 提示消息，成功时通常为 null */
    private String message;

    /** 处理过程中捕获到的异常，未发生异常时为 null */
    private Exception exception;

    /**
     * 构造带消息的成功结果。
     *
     * @param data    业务数据
     * @param message 提示消息
     * @param <T>     业务数据类型
     * @param <P>     请求参数类型
     * @return 成功结果（{@code isSuccess = true}）
     */
    public static <T, P> SfcResult<T, P> success(T data, String message) {
        return SfcResult.<T, P>builder()
                .isSuccess(true)
                .data(data)
                .message(message)
                .build();
    }

    /**
     * 构造不带消息的成功结果（{@code message} 为 null）。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @param <P>  请求参数类型
     * @return 成功结果（{@code isSuccess = true}）
     */
    public static <T, P> SfcResult<T, P> success(T data) {
        return SfcResult.<T, P>builder()
                .isSuccess(true)
                .data(data)
                .message(null)
                .build();
    }

    /**
     * 构造不带数据、不带消息的成功结果。
     *
     * @param <T> 业务数据类型
     * @param <P> 请求参数类型
     * @return 成功结果（{@code isSuccess = true}、{@code data} 与 {@code message} 均为 null）
     */
    public static <T, P> SfcResult<T, P> success() {
        return SfcResult.<T, P>builder()
                .isSuccess(true)
                .data(null)
                .message(null)
                .build();
    }
}
