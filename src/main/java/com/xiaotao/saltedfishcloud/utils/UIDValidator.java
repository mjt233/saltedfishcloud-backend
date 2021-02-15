package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;

public class UIDValidator {
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

    static public void validate(int uid) {
        validate(uid, false);
    }


}
