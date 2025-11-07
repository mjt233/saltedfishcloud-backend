package com.xiaotao.saltedfishcloud.model.config;

import com.xiaotao.saltedfishcloud.annotations.*;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.service.mail.MailProperties;
import lombok.Data;

/**
 * 系统常规配置选项
 */
@Data
@ConfigPropertyEntity(
        prefix = "sys",
        defaultKeyNameStrategy = ConfigKeyNameStrategy.UNDER_SCORE_CASE,
        groups = {
                @ConfigPropertiesGroup(id = "register", name = "注册配置", prefix = "register"),
                @ConfigPropertiesGroup(id = "store", name = "存储配置", prefix = "store"),
                @ConfigPropertiesGroup(id = "common", name = "常规", prefix = "common"),
                @ConfigPropertiesGroup(id = "thumbnail", name = "缩略图", prefix = "thumbnail")
        }
)
public class SysCommonConfig {
    @ConfigProperty(
            title = "注册邀请码",
            defaultValue = "114514",
            describe = "当开启“邀请码注册”时，游客注册账号所需的邀请码",
            group = "register"
    )
    private String regCode;

    @ConfigProperty(
            title = "启用邮箱注册",
            inputType = "switch",
            defaultValue = "false",
            describe = "允许使用邮箱注册",
            group = "register"
    )
    private Boolean enableEmailReg;

    @ConfigProperty(
            title = "启用邀请码注册",
            inputType = "switch",
            defaultValue = "true",
            describe = "允许使用邀请码注册",
            group = "register"
    )
    private Boolean enableRegCode;

    @ConfigProperty(
            value = "mode",
            title = "存储模式",
            defaultValue = "UNIQUE",
            describe = """
            系统基础存储系统的数据组织方式。
            RAW为原始存储，在目标存储系统上的文件结构与用户网盘的文件结构保持一致。
            UNIQUE为唯一存储，按文件哈希值统一组织文件，相同文件只会存储一份。
                    """,
            group = "store",
            inputType = "select",
            options = {
                    @ConfigSelectOption(title = "RAW", value = "RAW"),
                    @ConfigSelectOption(title = "UNIQUE", value = "UNIQUE"),
            }
    )
    private StoreMode storeMode;

    @ConfigProperty(
            title = "自动同步间隔(该功能已暂时弃用)",
            defaultValue = "-1",
            describe = "文件记录服务与存储服务文件信息自动执行同步的间隔。\n单位：分钟，-1关闭",
            group = "store"
    )
    private Long syncInterval;

    // todo 用bindPropertyEntity对整个bean动态绑定时，Version无法反序列化，需要支持自定义的反序列化
    @ConfigProperty(
            title = "系统版本",
            defaultValue = "1.0.0.0-SNAPSHOT",
            describe = "当前系统的主版本号",
            group = "common",
            readonly = true
    )
    private Version version;

    @ConfigProperty(
            title = "缩略图源文件最大大小",
            defaultValue = "128",
            describe = "尝试提取一个文件的缩略图时，文件大小超过该值时会忽略提取缩略图。该项单位为：MiB",
            group = "thumbnail"
    )
    private Double maxThumbnailResourceSize;

    @ConfigProperty(
            title = "停用缩略图缓存",
            defaultValue = "false",
            describe = "是否停用缩略图缓存(每次加载缩略图时，都重新从源文件中进行提取。谨慎开启该选项，可能会造成较高服务器压力，建议只在开发环境或在较低访问量的环境中开启)",
            group = "thumbnail",
            inputType = "switch"
    )
    private Boolean disableThumbnailCache;

    @ConfigProperty(
            title = "发信服务器配置",
            defaultValue = "1.0.0.0-SNAPSHOT",
            describe = "系统发送邮件时连接的发信服务器配置",
            inputType = "form",
            group = "common",
            readonly = true
    )
    private MailProperties mailProperties;
}
