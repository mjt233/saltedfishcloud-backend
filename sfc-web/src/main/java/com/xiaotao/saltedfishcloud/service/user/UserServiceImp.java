package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.redis.RedisDao;
import com.xiaotao.saltedfishcloud.dao.redis.TokenDao;
import com.xiaotao.saltedfishcloud.entity.ErrorInfo;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.helper.RedisKeyGenerator;
import com.xiaotao.saltedfishcloud.service.mail.MailMessageGenerator;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.Username;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;

import static com.xiaotao.saltedfishcloud.config.DiskConfig.ACCEPT_AVATAR_TYPE;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class UserServiceImp implements UserService {
    private final TokenDao tokenDao;
    private final UserDao userDao;
    private final JavaMailSender mailSender;
    private final MailMessageGenerator mailMessageGenerator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SysRuntimeConfig sysRuntimeConfig;

    @Override
    public User getUserByAccount(String account) {
        User user;
        if (account.indexOf('@') != -1) {
            user = userDao.getByEmail(account);
        } else {
            user = userDao.getUserByUser(account);
        }
        return user;
    }

    @Override
    public User getUserByEmail(String email) {
        return userDao.getByEmail(email);
    }

    @Override
    public User getUserById(Integer id) {
        return userDao.getUserById(id);
    }

    @Override
    public String sendBindEmail(Integer uid, String email) throws MessagingException, UnsupportedEncodingException {
        if (userDao.getByEmail(email) != null) throw new JsonException(ErrorInfo.EMAIL_EXIST);
        String code = StringUtils.getRandomString(6, false);
        redisTemplate.opsForValue().set(
                RedisKeyGenerator.getUserEmailValidKey(uid, email, MailValidateType.BIND_MAIL),
                code,
                Duration.ofMinutes(15)
        );
        mailSender.send(mailMessageGenerator.getBindNewMailCodeMessage(email, code));
        return code;
    }

    @Override
    public String sendResetPasswordEmail(String account) throws MessagingException, UnsupportedEncodingException {
        final User user = getUserByAccount(account);
        if (user == null) throw new JsonException(ErrorInfo.USER_NOT_EXIST);
        if (user.getEmail() == null || user.getEmail().length() == 0) throw new JsonException(ErrorInfo.EMAIL_NOT_SET);

        String code = StringUtils.getRandomString(6, false);
        redisTemplate.opsForValue().set(
                RedisKeyGenerator.getUserEmailValidKey(user.getId(), user.getEmail(), MailValidateType.RESET_PASSWORD),
                code,
                Duration.ofMinutes(15)
        );
        mailSender.send(mailMessageGenerator.getFindPasswordCodeMessage(user.getEmail(), code));
        return code;
    }

    @Override
    public void resetPassword(String email, String code, String password) {
        final User user = userDao.getByEmail(email);
        if (user == null) { throw new JsonException(ErrorInfo.USER_NOT_EXIST); }
        String key = RedisKeyGenerator.getUserEmailValidKey(user.getId(), user.getEmail(), MailValidateType.RESET_PASSWORD);
        String record = (String) redisTemplate.opsForValue().get(key);
        if (code == null || !code.equals(record)) { throw new JsonException(ErrorInfo.EMAIL_CODE_ERROR); }

        userDao.modifyPassword(user.getId(), SecureUtils.getPassswd(password));
        redisTemplate.delete(key);
        tokenDao.cleanUserToken(user.getUsername());
    }



    @Override
    public void setEmail(Integer uid, String email, String code) {
        final User user = userDao.getUserById(uid);
        if (user == null) { throw new JsonException(ErrorInfo.USER_NOT_EXIST); }
        String key = RedisKeyGenerator.getUserEmailValidKey(uid, email, MailValidateType.BIND_MAIL);
        String record = (String) redisTemplate.opsForValue().get(key);
        if (code == null || !code.equals(record)) { throw new JsonException(ErrorInfo.EMAIL_CODE_ERROR); }


        userDao.updateEmail(uid, email);
        redisTemplate.delete(key);
    }


    @Override
    public String sendRegEmail(String email) {
        // 判断邮件注册开关
        if (!sysRuntimeConfig.isEnableEmailReg()) throw new JsonException(ErrorInfo.EMAIL_REG_DISABLE);

        // 先判断邮箱是否已被使用
        User user = userDao.getByEmail(email);
        if (user != null) { throw new JsonException(ErrorInfo.EMAIL_EXIST); }


        String code = StringUtils.getRandomString(5, false);
        redisTemplate.opsForValue().set(RedisKeyGenerator.getRegCodeKey(email), code);
        try {
            mailSender.send(mailMessageGenerator.getRegCodeMessage(email, code));
        } catch (MessagingException | UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new JsonException(ErrorInfo.SYSTEM_ERROR);
        }

        return code;
    }

    @Override
    public void grant(int uid, int type) {
        if (type > 1 || type < 0) throw new IllegalArgumentException("不合法的用户类型");
        User user = userDao.getUserById(uid);
        if (user == null) throw new UserNoExistException(404, "用户不存在");
        if (type == User.TYPE_COMMON && "admin".equals(user.getUsername())) {
            throw new IllegalArgumentException("不允许撤销admin用户的管理员权限");
        }
        userDao.grant(uid, type);
        tokenDao.cleanUserToken(user.getUsername());
    }


    @Override
    public User getUserByUser(String user) throws UserNoExistException {
        User user1 = userDao.getUserByUser(user);
        if (user1 == null) {
            throw new UserNoExistException(-1, "用户" + user + "不存在");
        }
        return user1;
    }

    @Override
    public int modifyPasswd(Integer uid, String oldPassword, String newPassword) {
        User user = userDao.getUserById(uid);
        if (user == null) throw new UserNoExistException(404, "用户不存在");
        if (!SecureUtils.getPassswd(oldPassword).equals(user.getPwd())) {
            throw new JsonException(403, "原密码错误");
        }
        tokenDao.cleanUserToken(user.getUsername());
        return userDao.modifyPassword(uid, SecureUtils.getPassswd(newPassword));
    }

    @Override
    public int updateLoginDate(Integer uid) {

        return userDao.updateLoginDate(uid, new Date().getTime()/1000);
    }

    @Override
    public int addUser(@Username @Valid String user, String passwd, String email, String code, boolean isEmailCode) {

        if (isEmailCode && !sysRuntimeConfig.isEnableEmailReg()) {

            // 请求邮箱验证，判断是否开启
            throw new JsonException(ErrorInfo.EMAIL_REG_DISABLE);
        } else if (!isEmailCode && !sysRuntimeConfig.isEnableRegCode()) {

            // 请求注册邀请码验证，判断是否开启
            throw new JsonException(ErrorInfo.REG_CODE_DISABLE);
        } else if (isEmailCode) {

            String key = RedisKeyGenerator.getRegCodeKey(email);
            // 通过邮箱验证码注册
            String recordCode = (String)redisTemplate.opsForValue().get(key);
            if (!code.equals(recordCode)) {
                throw new JsonException(ErrorInfo.EMAIL_CODE_ERROR);
            }
            int ret = addUser(user, passwd, email, User.TYPE_COMMON);
            redisTemplate.delete(key);
            return ret;
        } else {

            // 通过注册邀请码注册
            if (!code.equals(DiskConfig.REG_CODE)) {
                throw new JsonException(ErrorInfo.REG_CODE_ERROR);
            }
            return addUser(user, passwd, email, User.TYPE_COMMON);
        }
    }

    @Override
    public int addUser(String user, String passwd, String email, Integer type) {
        var upperName = user.toUpperCase();
        if (User.SYS_NAME_PUBLIC.equals(upperName) || User.SYS_NAME_ADMIN.equals(upperName)) {
            throw new IllegalArgumentException("用户名" + user + "为系统保留用户名，不允许添加");
        }
        if (userDao.getByEmail(email) != null) throw new JsonException(ErrorInfo.EMAIL_EXIST);
        String pwd = SecureUtils.getPassswd(passwd);
        try {
            int res = userDao.addUser(user, pwd, email, type);
            redisTemplate.delete(RedisKeyGenerator.getRegCodeKey(email));
            return res;
        } catch (DuplicateKeyException e) {
            throw new JsonException(ErrorInfo.USER_EXIST);
        }
    }

    @Override
    public void setAvatar(String username, MultipartFile file) {

        //  文件属性约束：大小不得大于3MiB，限定文件类型
        if (file.getSize() > 1024*1024*3) {
            throw new JsonException(400, "文件过大");
        }
        String suffix = FileUtils.getSuffix(file.getOriginalFilename());
        if ( !ACCEPT_AVATAR_TYPE.contains(suffix)) {
            throw new JsonException(400, "不支持的格式，只支持jpg, jpeg, gif, png");
        }

        try {
            Path profileRoot = Paths.get(DiskConfig.getUserProfileRoot(username));
            Files.createDirectories(profileRoot);
            File[] avatars = profileRoot.toFile().listFiles(pathname -> pathname.getName().contains("avatar"));
            if (avatars != null && avatars.length != 0) {
                avatars[0].delete();
            }
            file.transferTo(Paths.get(profileRoot + "/avatar." + suffix));
        } catch (IOException e) {
            throw new JsonException(500, e.getMessage());
        }

    }
}
