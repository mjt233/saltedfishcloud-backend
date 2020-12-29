package com.xiaotao.saltedfishcloud.service;

import com.sun.istack.NotNull;
import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@Service
public class UserServiceImp implements UserService{
    @Resource
    private UserDao userDao;

    @Override
    public User getUserByUser(String user) throws UserNoExistException {
        User user1 = userDao.getUserByUser(user);
        if (user1 == null) {
            throw new UserNoExistException(-1, "用户" + user + "不存在");
        }
        return user1;
    }

    @Override
    public User getUserByToken(@NotNull String token) throws UserNoExistException {
        User user1 = userDao.getUserByToken(token);
        if (user1 == null) {
            throw new UserNoExistException(-1, "Token" + token + "所标识的用户不存在");
        }
        return user1;
    }

    @Override
    public int modifyPasswd(Integer uid, String oldPassword, String newPassword) {
        return 0;
    }

    @Override
    public int updateLoginDate(Integer uid) {

        int i = userDao.updateLoginDate(uid, new Date().getTime()/1000);
        return i;
    }

    @Override
    public int addUser(String user, String passwd, Integer type) {
        String pwd = SecureUtils.getPassswd(passwd);
        return userDao.addUser(user, pwd, SecureUtils.getToken(pwd), type);
    }
}
