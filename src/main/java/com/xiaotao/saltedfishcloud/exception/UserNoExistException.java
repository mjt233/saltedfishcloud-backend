package com.xiaotao.saltedfishcloud.exception;

import com.xiaotao.saltedfishcloud.utils.JsonResult;

public class UserNoExistException extends HasResultException{

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
        super();
    }
}
