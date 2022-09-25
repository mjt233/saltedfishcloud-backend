package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.model.ConfigNode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * 文件系统的参数描述对象
 */
@Getter
@Builder
@ToString
public class DiskFileSystemDescribe {

    /**
     * 名称描述
     */
    private final String name;

    /**
     * 支持的协议
     */
    private final String protocol;

    /**
     * 描述
     */
    private final String describe;

    /**
     * 是否为公共文件系统，允许任何人创建挂载点
     */
    private final boolean isPublic;

    /**
     * 参数配置节点，用于给用户创建挂载时提供输入和参数描述
     */
    private final List<ConfigNode> configNode;


}
