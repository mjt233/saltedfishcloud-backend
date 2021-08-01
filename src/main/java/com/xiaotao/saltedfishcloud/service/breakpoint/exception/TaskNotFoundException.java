package com.xiaotao.saltedfishcloud.service.breakpoint.exception;

/**
 * 断点续传任务不存在
 */
public class TaskNotFoundException extends RuntimeException {
    /**
     * @param id 任务ID
     */
    public TaskNotFoundException(String id) {
        super("断点续传任务" + id + "不存在或已被移除");
    }
}
