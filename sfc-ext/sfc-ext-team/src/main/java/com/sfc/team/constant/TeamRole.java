package com.sfc.team.constant;

import com.xiaotao.saltedfishcloud.service.permission.UidAccessLevel;
import lombok.Getter;

/**
 * 团队成员角色。
 * <p>
 * 权限递进：{@link #READ} &lt; {@link #WRITE} &lt; {@link #MANAGE} &lt; {@link #OWNER}，
 * 与核心 {@link UidAccessLevel} 映射：READ→READ、WRITE→WRITE、MANAGE/OWNER→MANAGE。
 */
@Getter
public enum TeamRole {
    /**
     * 只读成员：可查看/下载/预览团队文件
     */
    READ(1),

    /**
     * 读写成员：可上传/新建/改名/移动/删除/覆盖团队文件
     */
    WRITE(2),

    /**
     * 团队管理员：继承写权限，并可管理成员、修改团队信息
     */
    MANAGE(3),

    /**
     * 团队所有者：继承管理权限，且仅所有者可转移所有权、解散团队
     */
    OWNER(4);

    /**
     * 角色数值（存库）
     */
    private final int code;

    TeamRole(int code) {
        this.code = code;
    }

    /**
     * 按数值解析角色
     *
     * @param code 角色数值
     * @return 角色；无法识别时返回 null
     */
    public static TeamRole of(Integer code) {
        if (code == null) {
            return null;
        }
        for (TeamRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }

    /**
     * 将团队角色映射为核心 UID 访问级别
     *
     * @return 对应的访问级别
     */
    public UidAccessLevel toUidAccessLevel() {
        return switch (this) {
            case READ -> UidAccessLevel.READ;
            case WRITE -> UidAccessLevel.WRITE;
            case MANAGE, OWNER -> UidAccessLevel.MANAGE;
        };
    }
}
