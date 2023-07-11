package com.sfc.webshell.constans;

/**
 * WebShell用的消息队列主题
 */
public interface WebShellMQTopic {
    /**
     * 默认消费组
     */
    String DEFAULT_GROUP = "default_group";

    interface Prefix {
        /**
         * 接收输入字符的队列流
         */
        String INPUT_STREAM = "web_shell_input_";

        /**
         * 接收输出字符的队列流
         */
        String OUTPUT_STREAM = "web_shell_output_";

        String EXIT_BROADCAST = "web_shell_exit_";
    }
}
