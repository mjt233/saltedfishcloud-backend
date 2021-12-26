package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.validator.annotations.Username;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

@Validated
public interface UserService {

    /**
     * 发送注册验证码到邮箱中
     * @param email 邮箱
     * @return 发送的验证码内容
     */
    String sendRegEmail(String email);

    /**
     * 设置用户权限类型
     * @param uid 用户ID
     * @param type 用户类型，1管理员，0普通用户
     */
    void grant(int uid, int type);

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
     * 直接添加用户
     * @param user      用户名
     * @param passwd    密码原文（即密码原文）
     * @param email     用户邮箱
     * @param type      类型(可用UserType.COMMON或UserType.ADMIN)
     * @return          数据库操作影响的行数
     */
    int addUser(@Username @Valid String user, String passwd, String email, Integer type);

    /**
     * 通过邀请码或邮箱注册用户的形式添加用户
     * @param user          用户名
     * @param passwd        密码
     * @param email         注册邮箱
     * @param code          邀请码或邮箱注册码
     * @param isEmailCode   code是否为邮箱注册码
     * @return              数据库操作影响的行数
     */
    int addUser(@Username @Valid String user, String passwd, String email, String code, boolean isEmailCode);

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
