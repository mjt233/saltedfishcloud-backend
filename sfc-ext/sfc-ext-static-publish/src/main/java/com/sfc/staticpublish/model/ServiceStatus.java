package com.sfc.staticpublish.model;

import com.xiaotao.saltedfishcloud.model.ClusterNodeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务状态信息
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ServiceStatus {
    /**
     * 所属节点
     */
    private ClusterNodeInfo nodeInfo;

    /**
     * 服务端口
     */
    private Integer serverPort;

    /**
     * 是否运行中
     */
    private Boolean isRunning;

    /**
     * 错误信息
     */
    private String errorMsg;
}
