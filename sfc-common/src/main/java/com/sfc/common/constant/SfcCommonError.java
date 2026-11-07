package com.sfc.common.constant;

import com.sfc.common.exception.SfcErrorInfo;
import lombok.Getter;

/**
 * 通用模块业务错误码。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.constant.error.CommonError} 逐字迁移而来
 * （仅包名/枚举名与实现的接口名变更）。</p>
 *
 * <p><b>枚举常量及其 code/status/message 三元组不可改动</b>：这些数值是对外契约，
 * 一旦漂移会直接改变前端与微服务消费方拿到的错误码。</p>
 */
@Getter
public enum SfcCommonError implements SfcErrorInfo {
    // 通用模块
    FORMAT_ERROR(2001, 400, "格式错误"),
    INVALID_FILE_NAME(2002, 400, "无效的文件名"),
    NOT_ALLOW_PATH(2003, 400, "存在非法的文件名或路径"),
    SYSTEM_BUSY(4000, 500, "系统繁忙"),
    SYSTEM_FORBIDDEN(4001, 403, "权限不足"),
    SYSTEM_ERROR(4002, 500, "系统错误"),
    RESOURCE_NOT_FOUND(4003, 404, "无法根据id查询到数据"),
    MOUNT_POINT_FILE_RECORD_PROXY_ERROR(4004, 500, "挂载点文件记录委托错误");

    /** 业务错误标识码 */
    private final int code;

    /** 对应的HTTP响应码 */
    private final int status;

    /** 错误描述信息 */
    private final String message;

    /**
     * 构造错误码枚举项。
     *
     * @param code    业务错误标识码
     * @param status  对应的HTTP响应码
     * @param message 错误描述信息
     */
    SfcCommonError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
