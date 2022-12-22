package com.xiaotao.saltedfishcloud.service.async.task;

/**
 * 表示一个异步任务的执行结果，成功与否和消息保留自动过期时间
 */
public class AsyncTaskResult {
    public enum Status {
        SUCCESS, FAILED
    }
    public Status status;
    public long timeout;

    /**
     *
     * @param status    执行结果
     * @param timeout   超时时间，0表示永不自动过期，大于0则表示该数秒数后自动过期
     */
    public AsyncTaskResult(Status status, long timeout) {
        this.status = status;
        this.timeout = timeout;
    }

    /**
     * 获取一个实例
     * @param status    执行结果
     * @param timeout   超时时间，0表示永不自动过期，大于0则表示该数秒数后自动过期
     */
    public static AsyncTaskResult getInstance(Status status, long timeout) {
        return new AsyncTaskResult(status, timeout);
    }
}
