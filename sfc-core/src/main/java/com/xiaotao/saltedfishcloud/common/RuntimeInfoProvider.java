package com.xiaotao.saltedfishcloud.common;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class RuntimeInfoProvider implements SystemOverviewItemProvider {
    @Override
    public List<ConfigNode> provideItem(Map<String, ConfigNode> existItem) {

        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long used = total - runtime.freeMemory();
        int cpu = runtime.availableProcessors();
        return Collections.singletonList(
                ConfigNode.builder()
                        .title("系统运行信息")
                        .name("runtimeInfo")
                        .nodes(Arrays.asList(
                                new ConfigNode("CPU核心数", cpu + ""),
                                new ConfigNode("最大内存", max + ""),
                                new ConfigNode("总内存", total + ""),
                                new ConfigNode("已用内存", used + "")
                        ))
                        .build()
        );
    }
}
