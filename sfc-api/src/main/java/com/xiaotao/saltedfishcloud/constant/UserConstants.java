package com.xiaotao.saltedfishcloud.constant;

/**
 * 用户相关系统常量
 */
public final class UserConstants {
    private UserConstants() {}

    /** 公共用户/匿名用户系统名称 */
    public static final String SYS_NAME_PUBLIC = "__SYSTEM_PUBLIC";
    /** 系统管理员用户名 */
    public static final String SYS_NAME_ADMIN = "ADMIN";
    /** 公共用户组名称 */
    public static final String SYS_GROUP_NAME_PUBLIC = "__SYSTEM_PUBLIC_GROUP";
    /** 公共用户 ID */
    public static final long PUBLIC_USER_ID = 0;
    /** 管理员用户类型 */
    public static final int TYPE_ADMIN = 1;
    /** 普通用户类型 */
    public static final int TYPE_COMMON = 0;
}
