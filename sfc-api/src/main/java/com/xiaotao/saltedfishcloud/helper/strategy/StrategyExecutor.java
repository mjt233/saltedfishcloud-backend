package com.xiaotao.saltedfishcloud.helper.strategy;

/**
 * 对某些事件进行监听并能干预执行策略的执行器
 */
@FunctionalInterface
public interface StrategyExecutor<T> {
    StrategyResult<T> execute(T value);
}
