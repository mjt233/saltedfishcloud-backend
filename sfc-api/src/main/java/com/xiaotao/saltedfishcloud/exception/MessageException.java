package com.xiaotao.saltedfishcloud.exception;

/**
 * 标识异常为仅需处理消息，不需要关注调用堆栈和异常类名称<br>
 * 在系统统一异常捕获时，msg会保持和异常的message一致。
 */
public interface MessageException {
}
