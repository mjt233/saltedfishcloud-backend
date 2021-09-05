package com.xiaotao.saltedfishcloud.service.async.context;

@FunctionalInterface
public interface AsyncTackCallback {
    /**
     * 无论成功与否，异步任务执行完成时执行的方法
     */
    void action();
}
