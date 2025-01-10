package com.xiaotao.saltedfishcloud.constant;

import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.service.mq.MQTopic;

/**
 * 消息队列订阅主题
 */
public interface MQTopicConstants {

    /**
     * 系统保护级别切换消息
     */
    MQTopic<ProtectLevel> PROTECT_LEVEL_SWITCH = new MQTopic<>(() -> "xyy_protect_level_switch") {};

    /**
     * 集群节点上线
     */
    MQTopic<ClusterNodeInfo> CLUSTER_NODE_ONLINE = new MQTopic<>(() -> "cluster_node_online") {};

    /**
     * 配置项出现变更
     */
    MQTopic<NameValueType<String>> CONFIG_CHANGE = new MQTopic<>(() -> "config_change") {};

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
    }
}
