package com.xiaotao.saltedfishcloud.exception;

public class UnableOverwriteException extends JsonException {
    public UnableOverwriteException(String message) {
        super(message);
    }

    public UnableOverwriteException(Integer code, String msg) {
        super(code, msg);
    }
}
