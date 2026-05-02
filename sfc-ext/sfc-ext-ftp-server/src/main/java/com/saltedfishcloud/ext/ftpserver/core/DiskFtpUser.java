package com.saltedfishcloud.ext.ftpserver.core;

import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import org.apache.ftpserver.usermanager.impl.BaseUser;


public class DiskFtpUser extends BaseUser {
    private long id;
    private boolean admin;

    public DiskFtpUser(UserPrincipal user) {
        setId(user.getId());
        setName(user.getUsername());
        setPassword(user.getPassword());
        admin = user.isAdmin();
    }
    public boolean isAdmin() {
        return admin;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public static DiskFtpUser getAnonymousUser() {
        return new DiskFtpUser(UserPrincipal.publicUser());
    }

    public boolean isAnonymousUser() {
        return getName().equals(UserConstants.SYS_NAME_PUBLIC);
    }
}
