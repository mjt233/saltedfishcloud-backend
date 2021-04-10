package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    /**
     * 通过用户名获取用户信息
     * @param user  用户名
     * @return  用户对象
     * @throws UserNoExistException 用户不存在
     */
    User getUserByUser(String user) throws UserNoExistException;


    /**
     * 修改用户密码
     * @param uid           用户ID
     * @param oldPassword   旧密码
     * @param newPassword   新密码
     * @return              数据库操作影响的行数
     */
    int modifyPasswd(Integer uid, String oldPassword, String newPassword);

    /**
     * 添加用户
     * @param user      用户名
     * @param passwd    密码原文（即密码原文）
     * @param type      类型(可用UserType.COMMON或UserType.ADMIN)
     * @return          数据库操作影响的行数
     */
    int addUser(String user, String passwd, Integer type);

    /**
     * 更新用户的上次登录日期
     * @param uid   用户ID
     * @return      数据库操作影响的行数
     */
    int updateLoginDate(Integer uid);

    /**
     * 设置用户头像
     * @param username  用户名
     * @param file      头像文件
     */
    void setAvatar(String username, MultipartFile file);
}
