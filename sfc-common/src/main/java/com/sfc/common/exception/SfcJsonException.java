package com.sfc.common.exception;

import com.sfc.common.model.json.SfcJsonResult;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.exception.JsonException} 迁移而来，构造器与
 * 异常消息逻辑逐字保留，仅类型替换：{@code ErrorInfo → SfcErrorInfo}（同包）、
 * {@code JsonResult/JsonResultImpl → SfcJsonResult}。</p>
 *
 * <p><b>关于 {@link #res} 字段：</b>声明为原始类型 {@code SfcJsonResult}（不带类型参数），
 * 与宿主 {@code private final JsonResult res} 的原始类型写法完全一致。这样保留是为了让
 * 形如 {@code return e.getRes();}（如全局异常处理器的 {@code SfcJsonResult<Object>} 返回值）
 * 的调用点可以原样编译；若改成 {@code SfcJsonResult<?>}，此类调用点将需要显式转换。</p>
 */
public class SfcJsonException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -6859013370470905290L;

    /** 响应信封：异常对外暴露的 code/businessCode/msg */
    @Getter
    private final SfcJsonResult res;

    /** 对应的业务错误信息枚举/实现，非错误码构造时为 null */
    @Getter
    private SfcErrorInfo errorInfo;

    /**
     * 以纯文本消息构造异常（响应码固定为 500）。
     *
     * @param message 错误消息
     */
    public SfcJsonException(String message) {
        super(message);
        res = SfcJsonResult.getInstance(500, null, message);
    }

    /**
     * 直接以给定的响应信封构造异常。
     *
     * @param jsonResult 响应信封
     */
    public SfcJsonException(SfcJsonResult<?> jsonResult) {
        this.res = jsonResult;
    }

    /**
     * 以响应码与消息构造异常。
     *
     * @param code 响应码
     * @param msg  错误消息
     */
    public SfcJsonException(Integer code, String msg) {
        res = SfcJsonResult.getInstance(code, null, msg);
    }

    /**
     * 以响应码、细分业务码与消息构造异常。
     *
     * @param code         响应码
     * @param businessCode 细分业务码
     * @param msg          错误消息
     */
    public SfcJsonException(Integer code, Integer businessCode, String msg) {
        res = SfcJsonResult.getInstance(code, businessCode, null, msg);
    }

    /**
     * 构造默认的服务器错误异常（响应码 500，消息为"服务器其他错误"）。
     */
    public SfcJsonException() {
        res = SfcJsonResult.getInstance(500, null, "服务器其他错误");
    }

    /**
     * 以业务错误信息构造异常（响应码与业务码取自错误信息）。
     *
     * @param error 业务错误信息
     */
    public SfcJsonException(SfcErrorInfo error) {
        res = SfcJsonResult.getInstance(error.getStatus(), error.getCode(), null, error.getMessage());
        this.errorInfo = error;
    }

    /**
     * 以业务错误信息构造异常，并在错误描述后追加补充信息。
     *
     * @param error         业务错误信息
     * @param appendMessage 追加的消息（以空格与原始描述拼接）
     */
    public SfcJsonException(SfcErrorInfo error, String appendMessage) {
        res = SfcJsonResult.getInstance(error.getStatus(), error.getCode(), null, error.getMessage() + " " + appendMessage);
        this.errorInfo = error;
    }

    /**
     * 获取异常消息（取自响应信封的 msg）。
     *
     * @return 错误消息
     */
    @Override
    public String getMessage() {
        return res.getMsg();
    }
}
