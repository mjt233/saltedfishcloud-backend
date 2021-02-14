package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;

public class UIDValidator {
    static public void validate(int uid) {
        if (uid == 0) return;
        User springSecurityUser = SecureUtils.getSpringSecurityUser();
        if (springSecurityUser == null) {
            throw new HasResultException(-1, "未登录");
        }
        if (springSecurityUser.getId() != uid) {
            throw new HasResultException(403, "访问拒绝");
        }
    }
}
