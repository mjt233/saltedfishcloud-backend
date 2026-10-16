package com.xiaotao.saltedfishcloud.exception;

import com.sfc.cloud.model.json.SfcJsonResult;
import com.sfc.cloud.exception.SfcJsonException;

public class UserNoExistException extends SfcJsonException {
    private static final long serialVersionUID = -1973360070517009554L;

    public UserNoExistException(String message) {
        super(message);
    }

    public UserNoExistException(SfcJsonResult jsonResult) {
        super(jsonResult);
    }

    public UserNoExistException(Integer code, String msg) {
        super(code, msg);
    }

    public UserNoExistException() {
        super("用户不存在");
    }
}
