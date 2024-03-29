package com.xiaotao.saltedfishcloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统信息VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemInfoVO {
    /**
     * 每个cpu核心平均负载
     */
    private double cpuLoad;

    /**
     * 总内存
     */
    private long totalMemory;

    /**
     * 已用内存
     */
    private long usedMemory;

    /**
     * 内存使用率
     */
    private long memoryUsedRate;

    /**
     * 操作系统
     */
    private String os;

    /**
     * cpu名称
     */
    private String cpu;

    /**
     * cpu逻辑核心数
     */
    private int cpuLogicCount;

    /**
     * cpu物理核心数
     */
    private int cpuPhysicalCount;
}
