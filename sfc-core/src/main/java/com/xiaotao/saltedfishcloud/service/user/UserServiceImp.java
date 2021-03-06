package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.constant.error.AccountError;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.dao.redis.TokenDaoImpl;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.helper.RedisKeyGenerator;
import com.xiaotao.saltedfishcloud.service.mail.MailMessageGenerator;
import com.xiaotao.saltedfishcloud.service.mail.MailValidateType;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.Username;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Date;


@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class UserServiceImp implements UserService {
    private final TokenDaoImpl tokenDao;
    private final UserDao userDao;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SysProperties sysProperties;


    @Autowired
    @Lazy
    private JavaMailSender mailSender;

    @Autowired
    @Lazy
    private MailMessageGenerator mailMessageGenerator;

    @Autowired
    @Lazy
    private SysRuntimeConfig sysRuntimeConfig;

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
        if (id == 0) {
            return User.getPublicUser();
        } else {
            return userDao.getUserById(id);
        }
    }

    @Override
    public String sendBindEmail(Integer uid, String email) throws MessagingException, UnsupportedEncodingException {
        if (userDao.getByEmail(email) != null) throw new JsonException(AccountError.EMAIL_EXIST);
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
        if (user == null) throw new JsonException(AccountError.USER_NOT_EXIST);
        if (user.getEmail() == null || user.getEmail().length() == 0) throw new JsonException(AccountError.EMAIL_NOT_SET);

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
    public void resetPassword(String account, String code, String password) {
        final User user = getUserByAccount(account);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        String key = RedisKeyGenerator.getUserEmailValidKey(user.getId(), user.getEmail(), MailValidateType.RESET_PASSWORD);
        String record = (String) redisTemplate.opsForValue().get(key);
        if (code == null || !code.equals(record)) { throw new JsonException(AccountError.EMAIL_CODE_ERROR); }

        userDao.modifyPassword(user.getId(), SecureUtils.getPassswd(password));
        redisTemplate.delete(key);
        tokenDao.cleanUserToken(user.getId());
    }


    @Override
    public String sendVerifyEmail(Integer uid) throws MessagingException, UnsupportedEncodingException {
        final User user = getUserById(uid);
        if (user == null) throw new JsonException(AccountError.USER_NOT_EXIST);
        if (user.getEmail() == null || user.getEmail().length() == 0) throw new JsonException(AccountError.EMAIL_NOT_SET);

        String code = StringUtils.getRandomString(6, false);
        mailSender.send(mailMessageGenerator.getVerifyMailCodeMessage(user.getEmail(), code));
        redisTemplate.opsForValue().set(
                RedisKeyGenerator.getUserEmailValidKey(uid, user.getEmail(), MailValidateType.VERIFY_MAIL),
                code,
                Duration.ofMinutes(15)
        );
        return code;
    }

    @Override
    public void verifyEmail(Integer uid, String code) throws MessagingException, UnsupportedEncodingException {
        final User user = getUserById(uid);
        if (user == null) throw new JsonException(AccountError.USER_NOT_EXIST);
        if (user.getEmail() == null || user.getEmail().length() == 0) throw new JsonException(AccountError.EMAIL_NOT_SET);

        final Object record = redisTemplate.opsForValue().get(RedisKeyGenerator.getUserEmailValidKey(uid, user.getEmail(), MailValidateType.VERIFY_MAIL));
        if (!code.equals(record)) {
            throw new JsonException(AccountError.EMAIL_CODE_ERROR);
        }
    }

    @Override
    public void setEmail(Integer uid, String email) {
        final User user = userDao.getUserById(uid);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        userDao.updateEmail(uid, email);
    }


    @Override
    public void bindEmail(Integer uid, String email, String originCode, String newCode) {
        final User user = userDao.getUserById(uid);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        String originKey = null, originRecord = null, newKey = null, newRecord = null;

        // ???????????????
        if (user.getEmail() != null && user.getEmail().length() != 0) {
            originKey = RedisKeyGenerator.getUserEmailValidKey(uid, user.getEmail(), MailValidateType.VERIFY_MAIL);
            originRecord = (String) redisTemplate.opsForValue().get(originKey);
            if (originCode == null || !originCode.equals(originRecord)) { throw new JsonException(AccountError.EMAIL_CODE_ERROR); }
        }

        // ???????????????
        newKey = RedisKeyGenerator.getUserEmailValidKey(uid, email, MailValidateType.BIND_MAIL);
        newRecord = (String) redisTemplate.opsForValue().get(newKey);
        if (newCode == null || !newCode.equals(newRecord)) { throw new JsonException(AccountError.EMAIL_CODE_ERROR); }




        userDao.updateEmail(uid, email);
        if (originKey != null) redisTemplate.delete(originKey);
        redisTemplate.delete(newKey);
    }


    @Override
    public String sendRegEmail(String email) {
        // ????????????????????????
        if (!sysRuntimeConfig.isEnableEmailReg()) throw new JsonException(AccountError.EMAIL_REG_DISABLE);

        // ?????????????????????????????????
        User user = userDao.getByEmail(email);
        if (user != null) { throw new JsonException(AccountError.EMAIL_EXIST); }


        String code = StringUtils.getRandomString(5, false);
        redisTemplate.opsForValue().set(RedisKeyGenerator.getRegCodeKey(email), code, Duration.ofMinutes(15));
        try {
            mailSender.send(mailMessageGenerator.getRegCodeMessage(email, code));
        } catch (MessagingException | UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new JsonException(CommonError.SYSTEM_ERROR);
        }

        return code;
    }

    @Override
    public void grant(int uid, int type) {
        if (type > 1 || type < 0) throw new IllegalArgumentException("????????????????????????");
        User user = userDao.getUserById(uid);
        if (user == null) throw new UserNoExistException(404, "???????????????");
        if (type == User.TYPE_COMMON && "admin".equals(user.getUsername())) {
            throw new IllegalArgumentException("???????????????admin????????????????????????");
        }
        userDao.grant(uid, type);
        tokenDao.cleanUserToken(user.getId());
    }


    @Override
    public User getUserByUser(String user) throws UserNoExistException {
        User user1 = userDao.getUserByUser(user);
        if (user1 == null) {
            throw new UserNoExistException(-1, "??????" + user + "?????????");
        }
        return user1;
    }

    @Override
    public int modifyPasswd(Integer uid, String oldPassword, String newPassword) {
        User user = userDao.getUserById(uid);
        if (user == null) throw new UserNoExistException(404, "???????????????");
        if (!SecureUtils.getPassswd(oldPassword).equals(user.getPwd())) {
            throw new JsonException(403, "???????????????");
        }
        tokenDao.cleanUserToken(user.getId());
        return userDao.modifyPassword(uid, SecureUtils.getPassswd(newPassword));
    }

    @Override
    public int updateLoginDate(Integer uid) {

        return userDao.updateLoginDate(uid, new Date().getTime()/1000);
    }

    @Override
    public int addUser(@Username @Valid String user, String passwd, String email, String code, boolean isEmailCode) {

        if (isEmailCode && !sysRuntimeConfig.isEnableEmailReg()) {

            // ???????????????????????????????????????
            throw new JsonException(AccountError.EMAIL_REG_DISABLE);
        } else if (!isEmailCode && !sysRuntimeConfig.isEnableRegCode()) {

            // ????????????????????????????????????????????????
            throw new JsonException(AccountError.REG_CODE_DISABLE);
        } else if (isEmailCode) {

            String key = RedisKeyGenerator.getRegCodeKey(email);
            // ???????????????????????????
            String recordCode = (String)redisTemplate.opsForValue().get(key);
            if (!code.equals(recordCode)) {
                throw new JsonException(AccountError.EMAIL_CODE_ERROR);
            }
            int ret = addUser(user, passwd, email, User.TYPE_COMMON);
            redisTemplate.delete(key);
            return ret;
        } else {

            // ???????????????????????????
            if (!code.equals(sysProperties.getCommon().getRegCode())) {
                throw new JsonException(AccountError.REG_CODE_ERROR);
            }
            return addUser(user, passwd, null, User.TYPE_COMMON);
        }
    }

    @Override
    public int addUser(String user, String passwd, String email, Integer type) {
        var upperName = user.toUpperCase();
        if (User.SYS_NAME_PUBLIC.equals(upperName) || User.SYS_NAME_ADMIN.equals(upperName)) {
            throw new IllegalArgumentException("?????????" + user + "??????????????????????????????????????????");
        }
        if (email != null && email.length() != 0 && userDao.getByEmail(email) != null) throw new JsonException(AccountError.EMAIL_EXIST);
        String pwd = SecureUtils.getPassswd(passwd);
        try {
            int res = userDao.addUser(user, pwd, email, type);
            redisTemplate.delete(RedisKeyGenerator.getRegCodeKey(email));
            return res;
        } catch (DuplicateKeyException e) {
            throw new JsonException(AccountError.USER_EXIST);
        }
    }
}
