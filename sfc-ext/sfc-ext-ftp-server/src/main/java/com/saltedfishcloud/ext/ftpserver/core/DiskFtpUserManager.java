package com.saltedfishcloud.ext.ftpserver.core;

import com.saltedfishcloud.ext.ftpserver.FTPServerProperty;
import com.xiaotao.saltedfishcloud.dao.jpa.UserRepo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.apache.ftpserver.ftplet.*;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedList;
import java.util.List;


public class DiskFtpUserManager implements UserManager {
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private FTPServerProperty ftpServerProperty;

    @Override
    public BaseUser getUserByName(String username) {
        BaseUser ftpUser = new BaseUser();
        List<Authority> authorities = new LinkedList<>();

        authorities.add(new ConcurrentLoginPermission(0, 0));
        authorities.add(new TransferRatePermission(0, 0));
        ftpUser.setAuthorities(authorities);
        ftpUser.setName(username);
        ftpUser.setHomeDirectory("/");
        return ftpUser;
    }

    @Override
    public String[] getAllUserNames() throws FtpException {
        String[] users = userRepo.getUserList()
                .stream()
                .map(com.xiaotao.saltedfishcloud.model.po.User::getUser)
                .toArray(String[]::new);
        return users;
    }

    @Override
    public void delete(String username) throws FtpException {

    }

    @Override
    public void save(User user) throws FtpException {

    }

    @Override
    public boolean doesExist(String username) throws FtpException {
        return userRepo.getUserByUser(username) != null;
    }

    @Override
    public User authenticate(Authentication authentication) throws AuthenticationFailedException {
        if (authentication instanceof AnonymousAuthentication) {
            if (!ftpServerProperty.isEnableAnonymous()) {
                return null;
            }
            return getUserByName(com.xiaotao.saltedfishcloud.model.po.User.SYS_NAME_PUBLIC);
        }
        if (authentication instanceof UsernamePasswordAuthentication) {
            UsernamePasswordAuthentication auth = (UsernamePasswordAuthentication) authentication;
            com.xiaotao.saltedfishcloud.model.po.User user = userRepo.getUserByUser(auth.getUsername());
            if (user == null) return null;
            if (user.getPwd().equals(SecureUtils.getPassswd(auth.getPassword()))) {
                return getUserByName(auth.getUsername());
            }
        }
        return null;
    }

    @Override
    public String getAdminName() throws FtpException {
        return null;
    }

    @Override
    public boolean isAdmin(String username) throws FtpException {
        return userRepo.getUserByUser(username).getType() == com.xiaotao.saltedfishcloud.model.po.User.TYPE_ADMIN;
    }
}

