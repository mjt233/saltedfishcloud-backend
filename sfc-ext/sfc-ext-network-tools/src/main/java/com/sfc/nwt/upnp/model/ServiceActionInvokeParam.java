package com.sfc.nwt.upnp.model;

import com.xiaotao.saltedfishcloud.model.Pair;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * UPnP设备服务调用参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceActionInvokeParam {
    /**
     * 根设备的 UDN/USN
     */
    private String rootDeviceUSN;

    /**
     * 服务类型
     */
    private String serviceType;

    /**
     * 要调用的动作 action
     */
    private String action;

    /**
     * 调用请求参数
     */
    private List<Pair<String, String>> actionParams;
}
