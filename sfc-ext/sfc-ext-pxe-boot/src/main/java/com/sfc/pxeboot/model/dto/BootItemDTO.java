package com.sfc.pxeboot.model.dto;

import com.sfc.pxeboot.model.enums.BootItemType;
import com.sfc.pxeboot.model.enums.IsoBootMethod;
import lombok.Data;

/**
 * 启动项数据传输对象
 */
@Data
public class BootItemDTO {

    /**
     * 菜单显示名称
     */
    private String displayName;

    /**
     * iPXE 脚本标签唯一键
     */
    private String itemKey;

    /**
     * 启动项类型
     */
    private BootItemType type;

    /**
     * 网盘中的资源路径
     */
    private String resourcePath;

    /**
     * 内核文件名
     */
    private String kernelFilename;

    /**
     * initrd 文件名
     */
    private String initrdFilename;

    /**
     * 额外内核命令行参数
     */
    private String kernelParams;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 描述信息
     */
    private String description;

    /**
     * ISO 启动方式
     */
    private IsoBootMethod isoBootMethod;

    /**
     * 自定义 iPXE 脚本
     */
    private String customIpxeScript;
}
