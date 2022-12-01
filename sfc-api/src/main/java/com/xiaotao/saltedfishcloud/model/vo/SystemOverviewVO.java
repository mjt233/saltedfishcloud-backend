package com.xiaotao.saltedfishcloud.model.vo;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import lombok.Data;

import java.util.List;

/**
 * 系统总览数据
 */
@Data
public class SystemOverviewVO {
    /**
     * 主文件系统的存储状态
     */
    private List<FileSystemStatus> fileSystemStatus;

    /**
     * 系统其他状态属性，层级最大为2，最外面每一层均为一个分类。
     */
    private List<ConfigNode> systemStatus;
}
