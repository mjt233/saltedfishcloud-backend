package com.xiaotao.saltedfishcloud.exception;

import com.xiaotao.saltedfishcloud.po.JsonResult;

public class UserNoExistException extends HasResultException{
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
        super();
    }
}
