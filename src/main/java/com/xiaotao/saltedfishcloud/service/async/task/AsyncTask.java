package com.xiaotao.saltedfishcloud.service.async.task;

public interface AsyncTask<MT, ST> {

    void interrupt();

    boolean isExpire();

    /**
     * 向任务内部输入消息
     * @param msg 要输入的消息
     */
    void writeMessage(MT msg);

    /**
     * 读取任务的输出消息
     * @return 任务输出消息，若为空则为null
     */
    MT readMessage();


    /**
     * 开始执行任务，任务完成前，该方法应该处于阻塞状态
     * @return true为任务正常执行成功，false为任务执行失败
     */
    boolean start();

    /**
     * 任务是否已完成
     * @return true - 任务已完成，false - 任务未完成
     */
    boolean isFinish();

    /**
     * 获取任务的当前状态
     * @return 任务的当前状态
     */
    ST getStatus();
}
