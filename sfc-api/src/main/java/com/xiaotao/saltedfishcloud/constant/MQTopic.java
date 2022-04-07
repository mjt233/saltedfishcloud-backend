package com.xiaotao.saltedfishcloud.constant;

/**
 * 消息队列订阅主题
 */
public interface MQTopic {
    /**
     * 离线下载任务中断消息
     */
    String DOWNLOAD_TASK_INTERRUPT = "xyy_download_interrupt";

    /**
     * 系统保护级别切换消息
     */
    String PROTECT_LEVEL_SWITCH = "xyy_protect_level_switch";
}
