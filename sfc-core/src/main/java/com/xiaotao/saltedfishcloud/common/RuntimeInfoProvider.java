package com.xiaotao.saltedfishcloud.common;

import com.sun.management.OperatingSystemMXBean;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.SystemInfoVO;
import com.xiaotao.saltedfishcloud.service.manager.AdminService;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class RuntimeInfoProvider implements SystemOverviewItemProvider {
    @Override
    public long getProvideOrder() {
        return 0;
    }

    @Override
    public List<ConfigNode> provideItem(Map<String, ConfigNode> existItem) {

        Runtime runtime = Runtime.getRuntime();

        OperatingSystemMXBean operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalPhysicalMemorySize = operatingSystemMXBean.getTotalPhysicalMemorySize();
        long freePhysicalMemorySize = operatingSystemMXBean.getFreePhysicalMemorySize();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        int cpu = runtime.availableProcessors();
        return Collections.singletonList(
                ConfigNode.builder()
                        .title("系统运行信息")
                        .name("runtimeInfo")
                        .nodes(Arrays.asList(
                                new ConfigNode("CPU核心数", cpu + ""),
                                new ConfigNode("操作系统类型", OSInfo.isWindows() ? "Windows" : "Unix (like)"),
                                new ConfigNode("服务器内存总量", StringUtils.getFormatSize(totalPhysicalMemorySize)),
                                new ConfigNode("服务器剩余内存", StringUtils.getFormatSize(freePhysicalMemorySize)),
                                new ConfigNode("JVM最大可用内存", StringUtils.getFormatSize(max)),
                                new ConfigNode("JVM已用内存", StringUtils.getFormatSize(used))
                        ))
                        .build()
        );
    }
}
