package com.xiaotao.saltedfishcloud.service.file;

import lombok.Getter;
import lombok.ToString;

/**
 * 文件系统的参数描述对象
 */
@Getter
@ToString
public class DiskFileSystemDescribe {

    public DiskFileSystemDescribe(String name, String protocol, String describe) {
        this.name = name;
        this.protocol = protocol;
        this.describe = describe;
    }

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
}
