package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.UserDao;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.ftplet.*;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class DiskFtpUserManager implements UserManager {
    @Resource
    private UserDao userDao;

    @Override
    public User getUserByName(String username) throws FtpException {
        BaseUser ftpUser = new BaseUser();
        if (username.equals("anonymous")) {
            ftpUser.setName(username);
            ftpUser.setHomeDirectory(DiskConfig.PUBLIC_ROOT);
            log.info("匿名登录");
            return ftpUser;
        }
        com.xiaotao.saltedfishcloud.po.User user = userDao.getUserByUser(username);

        if (user == null) {
            throw new FtpException("用户" + username + "不存在");
        }
        ftpUser.setName(user.getUsername());
        ftpUser.setPassword(user.getPassword());
        return ftpUser;
    }

    @Override
    public String[] getAllUserNames() throws FtpException {
        return new String[0];
    }

    @Override
    public void delete(String username) throws FtpException {

    }

    @Override
    public void save(User user) throws FtpException {

    }

    @Override
    public boolean doesExist(String username) throws FtpException {
        return true;
    }

    @Override
    public User authenticate(Authentication authentication) throws AuthenticationFailedException {
        return null;
    }

    @Override
    public String getAdminName() throws FtpException {
        return null;
    }

    @Override
    public boolean isAdmin(String username) throws FtpException {
        return false;
    }
}

