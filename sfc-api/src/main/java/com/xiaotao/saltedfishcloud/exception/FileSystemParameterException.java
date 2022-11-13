package com.xiaotao.saltedfishcloud.exception;

/**
 * 文件系统的参数异常
 */
public class FileSystemParameterException extends Exception {
    public FileSystemParameterException() {
        super();
    }

    public FileSystemParameterException(String message) {
        super(message);
    }

    public FileSystemParameterException(String message, Throwable cause) {
        super(message, cause);
    }
}
