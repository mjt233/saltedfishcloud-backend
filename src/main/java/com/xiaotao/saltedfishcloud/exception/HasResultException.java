package com.xiaotao.saltedfishcloud.exception;

import com.xiaotao.saltedfishcloud.utils.JsonResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class HasResultException extends Exception {
    private JsonResult jsonResult;

    public HasResultException(String message) {
        super(message);
        jsonResult = JsonResult.getInstance(-1, null, message);
    }

    public HasResultException(JsonResult jsonResult) {
        this.jsonResult = jsonResult;
    }

    public HasResultException(Integer code, String msg) {
        jsonResult = JsonResult.getInstance(code, null, msg);
    }

    public HasResultException() {
        jsonResult = JsonResult.getInstance(500, null, "服务器其他错误");
    }
}
