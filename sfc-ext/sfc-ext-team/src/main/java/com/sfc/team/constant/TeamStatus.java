package com.sfc.team.constant;

/**
 * 团队相关实体状态常量。
 */
public final class TeamStatus {
    /**
     * 团队状态：正常
     */
    public static final int TEAM_NORMAL = 1;

    /**
     * 团队状态：已解散（保留行用于审计）
     */
    public static final int TEAM_DISSOLVED = 2;

    /**
     * 成员状态：正常
     */
    public static final int MEMBER_ACTIVE = 1;

    /**
     * 成员状态：已移除
     */
    public static final int MEMBER_REMOVED = 2;

    /**
     * 邀请状态：待接受
     */
    public static final int INVITE_PENDING = 1;

    /**
     * 邀请状态：已接受
     */
    public static final int INVITE_ACCEPTED = 2;

    /**
     * 邀请状态：已撤销
     */
    public static final int INVITE_REVOKED = 3;

    private TeamStatus() {
    }
}
