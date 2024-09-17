package com.xiaotao.saltedfishcloud.exception;

import com.xiaotao.saltedfishcloud.model.json.JsonResult;

public class UserNoExistException extends JsonException {
    private static final long serialVersionUID = -1973360070517009554L;

    public UserNoExistException(String message) {
        super(message);
    }

    public UserNoExistException(JsonResult jsonResult) {
        super(jsonResult);
    }

    public UserNoExistException(Integer code, String msg) {
        super(code, msg);
    }

    public UserNoExistException() {
        super("用户不存在");
    }
}
