package com.sfc.task.prog;

/**
 * 速度检测器
 */
public interface ProgressDetector {

    /**
     * 将一个进度状态接口纳入到检测，并为其更新速度值
     * @param progressProvider   进度检测接口
     * @param id                 进度任务唯一标识
     */
    void addObserve(ProgressProvider progressProvider, String id);

    /**
     * 移除一个监听中的进度状态监视器
     * @param id    进度任务唯一标识
     * @return      是否成功移除
     */
    boolean removeObserve(String id);

    /**
     * 通过任务标识获取只读的进度记录
     * @param id    任务标识
     * @return      进度记录
     */
    ProgressRecord getRecord(String id);
}
