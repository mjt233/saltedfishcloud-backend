package com.sfc.constant;

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

    /**
     * 集群节点上线
     */
    String CLUSTER_NODE_ONLINE = "cluster_node_online";

    /**
     * 配置项出现变更
     */
    String CONFIG_CHANGE = "config_change";

    /**
     * 系统重启
     */
    String RESTART = "restart";

    /**
     * 前缀类型的消息主题
     */
    interface Prefix {

        /**
         * Redis Stream前缀
         */
        String STREAM_PREFIX = "stream_";

        /**
         * 异步任务执行退出消息
         */
        String ASYNC_TASK_EXIT = "async_task_exit/";

        /**
         * 异步任务日志更新
         */
        String ASYNC_TASK_LOG = "async_task_log/";
    }
}
