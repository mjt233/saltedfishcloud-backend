package com.sfc.rpc.enums;

/**
 * RPC响应处理策略
 */
public enum RPCResponseStrategy {
    /**
     * 只接受一个节点的有效处理响应
     */
    ONLY_ACCEPT_ONE,

    /**
     * 汇总所有节点的有效响应结果，使用该策略的RPC动作方法返回值应为{@link java.util.List}
     */
    SUMMARY_ALL
}
