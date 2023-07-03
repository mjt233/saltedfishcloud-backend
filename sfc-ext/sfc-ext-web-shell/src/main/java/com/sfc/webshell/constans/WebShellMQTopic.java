package com.sfc.webshell.constans;

/**
 * WebShell用的消息队列主题
 */
public interface WebShellMQTopic {
    String DEFAULT_GROUP = "default_group";
    interface Prefix {
        String INPUT = "web_shell_input_";
        String OUTPUT = "web_shell_output_";
    }
}
