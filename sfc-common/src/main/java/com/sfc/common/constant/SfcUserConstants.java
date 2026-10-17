package com.sfc.common.constant;

/**
 * 用户相关系统常量。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.constant.UserConstants} 逐字迁移而来
 * （仅包名/类名变更），<b>所有常量的值均逐字保留</b>：它们参与数据库记录与鉴权判断
 * （例如公共用户 ID 为 0、管理员类型为 1），改动会导致数据语义变化。</p>
 */
public final class SfcUserConstants {
    private SfcUserConstants() {}

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
