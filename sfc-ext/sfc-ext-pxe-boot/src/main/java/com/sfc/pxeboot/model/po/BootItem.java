package com.sfc.pxeboot.model.po;

import com.sfc.pxeboot.model.enums.BootItemType;
import com.sfc.pxeboot.model.enums.IsoBootMethod;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * PXE 启动项实体类
 */
@Entity
@Table(indexes = {
    @Index(name = "idx_pxe_boot_item_enabled", columnList = "enabled"),
    @Index(name = "idx_pxe_boot_item_sort_order", columnList = "sort_order")
})
@Getter
@Setter
public class BootItem extends AuditModel {

    /**
     * 菜单显示名称
     */
    @Column(nullable = false)
    private String displayName;

    /**
     * iPXE 脚本标签唯一键
     */
    @Column(nullable = false)
    private String itemKey;

    /**
     * 启动项的资源类型
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BootItemType type;

    /**
     * 网盘中的资源路径：
     * - ISO: ISO 文件路径 (如 "/boot-images/ubuntu-22.04.iso")
     * - DIRECTORY: 目录路径 (如 "/boot-images/netboot/")
     * - KERNEL_INITRD: 包含 kernel 和 initrd 的目录路径
     */
    @Column(nullable = false)
    private String resourcePath;

    /**
     * 内核文件名（KERNEL_INITRD 类型使用）
     */
    private String kernelFilename;

    /**
     * initrd 文件名（KERNEL_INITRD 类型使用）
     */
    private String initrdFilename;

    /**
     * 额外内核命令行参数
     */
    @Column(length = 1024)
    private String kernelParams;

    /**
     * 是否在启动菜单中启用
     */
    private Boolean enabled = true;

    /**
     * 菜单排序顺序
     */
    @Column
    private Integer sortOrder = 0;

    /**
     * 可选的描述信息，用于 iPXE 菜单中显示
     */
    @Column(length = 512)
    private String description;

    /**
     * 自定义的 iPXE 启动脚本段落。
     */
    @Lob
    private String customIpxeScript;

    /**
     * ISO 启动方式
     */
    @Enumerated(EnumType.STRING)
    private IsoBootMethod isoBootMethod;
}
