package com.xiaotao.saltedfishcloud.exception;

import com.sfc.common.exception.SfcJsonException;

public class UnableOverwriteException extends SfcJsonException {
    public UnableOverwriteException(String message) {
        super(message);
    }

    public UnableOverwriteException(Integer code, String msg) {
        super(code, msg);
    }
}
