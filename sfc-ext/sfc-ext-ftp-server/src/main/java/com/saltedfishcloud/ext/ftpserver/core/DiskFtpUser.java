package com.saltedfishcloud.ext.ftpserver.core;

import com.xiaotao.saltedfishcloud.model.po.User;
import org.apache.ftpserver.usermanager.impl.BaseUser;


public class DiskFtpUser extends BaseUser {
    private long id;
    private boolean admin;

    public DiskFtpUser(User user) {
        setId(user.getId());
        setName(user.getUsername());
        setPassword(user.getPassword());
        admin = user.getType() == User.TYPE_ADMIN;
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
        return new DiskFtpUser(User.getPublicUser());
    }

    public boolean isAnonymousUser() {
        return getName().equals(User.SYS_NAME_PUBLIC);
    }
}
