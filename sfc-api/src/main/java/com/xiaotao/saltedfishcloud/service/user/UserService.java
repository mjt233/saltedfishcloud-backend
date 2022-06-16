package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.validator.annotations.Username;
import org.springframework.validation.annotation.Validated;

import javax.mail.MessagingException;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;

@Validated
public interface UserService {
    /**
     * 通过账号标识字段获取用户
     * @param account   邮箱或用户名
     * @return  用户
     */
    User getUserByAccount(String account);

    User getUserByEmail(String email);

    User getUserById(Integer id);

    /**
     * 通过邮箱验证方式重置用户密码
     * @param account   邮箱或用户名
     * @param code      验证码
     * @param password  新密码
     */
    void resetPassword(String account, String code, String password);

    /**
     * 设置新的邮箱
     * @param uid   用户ID
     * @param email 新邮箱
     */
    void setEmail(Integer uid, String email);

    /**
     * 绑定新邮箱
     * @param uid           用户ID
     * @param newEmail      新邮件地址
     * @param originCode    旧邮箱的验证码，若用户原本没有邮箱则可为空
     * @param newCode       新邮箱的验证码
     */
    void bindEmail(Integer uid, String newEmail, String originCode, String newCode);

    /**
     * 发送用户新邮箱绑定用的邮箱验证码。
     * 新邮箱不能在系统中被使用，否则抛出异常
     * @param uid   待绑定新邮箱的用户ID
     * @param email 新邮箱
     * @return      验证码，注意不要暴露到响应
     */
    String sendBindEmail(Integer uid, String email) throws MessagingException, UnsupportedEncodingException;

    /**
     * 发送旧邮箱验证码，用于验证用户拥有该邮箱地址
     * @param uid   用户ID
     * @return 验证码，注意不要暴露到响应
     */
    String sendVerifyEmail(Integer uid) throws MessagingException, UnsupportedEncodingException;

    /**
     * 验证用户的邮箱验证码是否正确。验证码来源见{@link UserService#sendVerifyEmail(Integer)}
     * 若验证失败则抛异常
     * @param uid   用户ID
     * @param code  邮箱验证码
     */
    void verifyEmail(Integer uid, String code) throws MessagingException, UnsupportedEncodingException;

    /**
     * 发送用户重置密码用的邮箱验证码。
     * 若用户未绑定邮箱，则抛出异常
     * @param account   邮箱或用户名或ID
     * @return  验证码，注意不要暴露到响应
     */
    String sendResetPasswordEmail(String account) throws MessagingException, UnsupportedEncodingException;

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
}
