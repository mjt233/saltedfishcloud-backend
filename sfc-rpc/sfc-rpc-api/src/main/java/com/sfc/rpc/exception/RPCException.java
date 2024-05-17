package com.sfc.rpc.exception;

public class RPCException extends RuntimeException {
    public RPCException() {
    }

    public RPCException(String message) {
        super(message);
    }

    public RPCException(String message, Throwable cause) {
        super(message, cause);
    }
}
