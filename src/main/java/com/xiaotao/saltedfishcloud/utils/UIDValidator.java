package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;

public class UIDValidator {
    /**
     * 验证某个UID是否有权限对某个UID的资源进行操作。
     * 若非管理员账号对非己UID资源进行操作，将会被拒绝
     * @param uid 用户ID
     * @param onlyAdmin 是否只允许管理员通过验证
     */
    static public void validate(int uid, boolean onlyAdmin) {
        if (!onlyAdmin && uid == 0) return;
        User springSecurityUser = SecureUtils.getSpringSecurityUser();
        if (springSecurityUser == null) {
            throw new HasResultException(-1, "未登录");
        }

        boolean isAdmin = springSecurityUser.getType() == User.TYPE_ADMIN;
        if ( (springSecurityUser.getId() != uid && !isAdmin) ||
                (onlyAdmin && uid == 0 && !isAdmin) ) {
            throw new HasResultException(403, "访问拒绝");
        }
    }

    /**
     * 验证某个UID是否有权限对某个UID的资源进行操作。
     * 若非管理员账号对非己UID资源进行操作，将会被拒绝
     * @param uid 用户ID
     */
    static public void validate(int uid) {
        validate(uid, false);
    }


}
