package com.xiaotao.saltedfishcloud.exception;

/**
 * 不支持的文件系统协议，对应协议的文件系统可能未被注册、被删除、或使用了错误的协议名称
 */
public class UnsupportedFileSystemProtocolException extends RuntimeException {
    public UnsupportedFileSystemProtocolException() {
        super();
    }

    public UnsupportedFileSystemProtocolException(String message) {
        super("不支持的文件挂载系统协议：" + message);
    }

    public UnsupportedFileSystemProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
