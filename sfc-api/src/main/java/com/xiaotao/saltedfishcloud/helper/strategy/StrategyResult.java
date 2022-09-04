package com.xiaotao.saltedfishcloud.helper.strategy;

/**
 * 经过策略决定后的结果
 */
public interface StrategyResult<T> {
    /**
     * 是否继续执行流程
     */
    boolean isContinue();

    /**
     * 是否为错误结果
     */
    boolean isError();

    /**
     * 获取消息
     */
    String getMessage();

    /**
     * 获取经过决策后的新值
     */
    T getValue();
}
