package com.sfc.common.exception;

/**
 * 业务错误信息契约。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.constant.error.ErrorInfo} 逐字迁移而来
 * （仅包名/接口名变更），方法签名一字未改。实现类通常为错误码枚举
 * （如 {@code com.sfc.cloud.constant.SfcCommonError}）。</p>
 */
public interface SfcErrorInfo {
    /**
     * 获取错误描述信息
     */
    String getMessage();

    /**
     * 获取错误对应的HTTP响应码
     */
    int getStatus();

    /**
     * 获取错误的业务错误标识码
     */
    int getCode();
}
