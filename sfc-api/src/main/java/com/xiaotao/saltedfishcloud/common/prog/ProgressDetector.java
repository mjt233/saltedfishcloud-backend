package com.xiaotao.saltedfishcloud.common.prog;

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
     * 通过任务标识获取只读的进度记录
     * @param id    任务标识
     * @return      进度记录
     */
    ProgressRecord getRecord(String id);
}
