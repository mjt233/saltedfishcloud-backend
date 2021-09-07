package com.xiaotao.saltedfishcloud.service.async.context;

/**
 * 一个空的异步任务回调
 */
public class EmptyCallback implements AsyncTackCallback {
    public final static EmptyCallback inst = new EmptyCallback();
    private EmptyCallback() {}
    @Override
    public void action() {

    }
    public static AsyncTackCallback get() {
        return inst;
    }
}
