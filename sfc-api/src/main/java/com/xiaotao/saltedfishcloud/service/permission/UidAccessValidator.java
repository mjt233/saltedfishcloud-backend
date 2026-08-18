package com.xiaotao.saltedfishcloud.service.permission;

import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;

/**
 * UID 访问级别校验工具。
 * <p>
 * 提供带级别的访问校验（读写区分），供核心文件写操作与插件鉴权复用：
 * 本人/管理员恒通过，公共资源遵循「读开放、写仅管理员」的既有规则，
 * 其余 uid 咨询 {@link UidAccessResolver}（团队空间等插件提供的访问级别）。
 */
public final class UidAccessValidator {

    private UidAccessValidator() {
    }

    /**
     * 校验当前登录用户对指定 uid 的访问级别是否至少为 minLevel。
     *
     * @param uid      目标资源所属 UID
     * @param minLevel 要求的最低访问级别
     * @return 通过返回 true
     */
    public static boolean validate(long uid, UidAccessLevel minLevel) {
        return validate(SecureUtils.getSpringSecurityUser(), uid, minLevel);
    }

    /**
     * 校验指定用户对指定 uid 的访问级别是否至少为 minLevel。
     *
     * @param user     用户（null 视为匿名，直接不通过）
     * @param uid      目标资源所属 UID
     * @param minLevel 要求的最低访问级别
     * @return 通过返回 true
     */
    public static boolean validate(UserPrincipal user, long uid, UidAccessLevel minLevel) {
        if (user == null) {
            return false;
        }
        // 管理员拥有全部权限
        if (user.isAdmin()) {
            return true;
        }
        // 本人对本人资源拥有完全权限
        if (user.getId() != null && user.getId() == uid) {
            return true;
        }
        // 公共资源：读开放、写（及以上）仅管理员（管理员已在上方放行）
        if (uid == UserConstants.PUBLIC_USER_ID) {
            return minLevel.getOrder() <= UidAccessLevel.READ.getOrder();
        }
        UidAccessResolver resolver = UidAccessResolver.getInstance();
        if (resolver == null) {
            return false;
        }
        UidAccessLevel level = resolver.getAccessLevel(user, uid);
        return level != null && level.getOrder() >= minLevel.getOrder();
    }

    /**
     * 校验当前登录用户对指定 uid 的访问级别至少为 minLevel，不通过则抛出禁止异常。
     *
     * @param uid      目标资源所属 UID
     * @param minLevel 要求的最低访问级别
     * @see CommonError#SYSTEM_FORBIDDEN
     */
    public static void validateWithException(long uid, UidAccessLevel minLevel) {
        if (!validate(uid, minLevel)) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
    }

    /**
     * 当 uid 由提供者管理（如团队空间）时，校验当前用户对其访问级别至少为 WRITE。
     * <p>
     * 用于移动操作等仅有读校验的入口：若目标空间由提供者管理且用户只有读权限，
     * 则禁止将文件移出该空间（移动会改变源空间内容）。非提供者管理的 uid（个人/公共空间）
     * 不做额外收紧，保持既有行为不变。
     *
     * @param uid 源空间 UID
     */
    public static void validateTeamModifyIfProviderManaged(long uid) {
        UserPrincipal user = SecureUtils.getSpringSecurityUser();
        if (user == null || user.isAdmin()) {
            return;
        }
        if (user.getId() != null && user.getId() == uid) {
            return;
        }
        UidAccessResolver resolver = UidAccessResolver.getInstance();
        if (resolver == null) {
            return;
        }
        UidAccessLevel level = resolver.getAccessLevel(user, uid);
        if (level != null && level != UidAccessLevel.NONE
                && level.getOrder() < UidAccessLevel.WRITE.getOrder()) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
    }
}
