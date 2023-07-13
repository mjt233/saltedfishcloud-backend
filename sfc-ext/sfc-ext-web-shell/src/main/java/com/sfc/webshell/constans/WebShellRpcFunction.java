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

    /**
     * 对会话进行重命名操作
     */
    String RENAME_SESSION = "web_shell__rename_session";

    /**
     * 重启会话
     */
    String RESTART = "web_shell__restart";

    /**
     * 重置pty终端大小
     */
    String RESIZE_PTY = "web_shell__resize";

    /**
     * 移除会话
     */
    String REMOVE = "web_shell__remove";
}
