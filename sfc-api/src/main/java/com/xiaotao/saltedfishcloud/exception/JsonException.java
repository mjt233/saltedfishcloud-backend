package com.xiaotao.saltedfishcloud.exception;

import com.sfc.constant.error.ErrorInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import lombok.Getter;

import javax.persistence.Transient;

public class JsonException extends RuntimeException {
    private static final long serialVersionUID = -6859013370470905290L;

    @Getter
    private final JsonResult res;

    @Getter
    private ErrorInfo errorInfo;

    public JsonException(String message) {
        super(message);
        res = JsonResultImpl.getInstance(500, null, message);
    }

    public JsonException(JsonResult jsonResult) {
        this.res = jsonResult;
    }

    public JsonException(Integer code, String msg) {
        res = JsonResultImpl.getInstance(code, null, msg);
    }

    public JsonException(Integer code, Integer businessCode, String msg) {
        res = JsonResultImpl.getInstance(code, businessCode, null, msg);
    }

    public JsonException() {
        res = JsonResultImpl.getInstance(500, null, "服务器其他错误");
    }

    public JsonException(ErrorInfo error) {
        res = JsonResultImpl.getInstance(error.getStatus(), error.getCode(), null, error.getMessage());
        this.errorInfo = error;
    }

    @Override
    public String getMessage() {
        return res.getMsg();
    }
}
