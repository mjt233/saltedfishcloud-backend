package com.sfc.webshell.constans;

/**
 * WebShell RPC函数注册名称
 */
public interface WebShellRpcFunction {
    /**
     * 干掉进程
     */
    String KILL = "web_shell__kill";

    /**
     * 获取会话列表
     */
    String LIST_SESSION = "web_shell__list_session";

    /**
     * 获取会话最近的历史输出
     */
    String GET_OUTPUT_LOG = "web_shell__get_output_log";
}
