package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.constant.error.AccountError;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.dao.jpa.UserRepo;
import com.xiaotao.saltedfishcloud.dao.redis.TokenServiceImpl;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.helper.RedisKeyGenerator;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserInfo;
import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import com.xiaotao.saltedfishcloud.service.mail.MailMessageGenerator;
import com.xiaotao.saltedfishcloud.service.mail.MailValidateType;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.Username;
import jakarta.mail.MessagingException;
import jakarta.servlet.ServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;


@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class UserServiceImp implements UserService {
    private final TokenServiceImpl tokenDao;
    private final UserRepo userRepo;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SysCommonConfig sysCommonConfig;


    @Autowired
    @Lazy
    private JavaMailSender mailSender;

    @Autowired
    @Lazy
    private MailMessageGenerator mailMessageGenerator;

    @Autowired
    @Lazy
    private LogRecordManager logRecordManager;

    @Override
    public User getUserByAccount(String account) {
        User user;
        if (account.indexOf('@') != -1) {
            user = userRepo.getByEmail(account);
        } else {
            user = userRepo.getUserByUser(account);
        }
        return user;
    }

    @Override
    public CommonPageInfo<User> listUsers(PageableRequest request) {
        int page = Optional.ofNullable(request.getPage()).orElse(0);
        int size = Optional.ofNullable(request.getSize()).orElse(10);
        return CommonPageInfo.of(userRepo.findAll(PageRequest.of(page, size)).map(UserRepo::toUser));
    }

    @Override
    public CommonPageInfo<User> searchUsers(String keyword, PageableRequest request) {
        int page = Optional.ofNullable(request.getPage()).orElse(0);
        int size = Optional.ofNullable(request.getSize()).orElse(10);

        String k = keyword
                .replaceAll("\\\\", "\\\\\\\\")
                .replaceAll("_", "\\\\_")
                .replaceAll("%", "\\\\%");
        Page<User> res = userRepo.searchUsers(
                k,
                PageRequest.of(page, size)
        ).map(UserRepo::toUser);
        return CommonPageInfo.of(res);
    }

    @Override
    public List<User> findBaseInfoByIds(Collection<Long> ids) {
        return userRepo.findBaseInfoByIds(ids);
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepo.getByEmail(email);
    }

    @Override
    public User getUserById(Long id) {
        if (id == 0) {
            return User.getPublicUser();
        } else {
            return userRepo.getUserById(id);
        }
    }

    @Override
    public String sendBindEmail(Long uid, String email) throws MessagingException, UnsupportedEncodingException {
        if (userRepo.getByEmail(email) != null) throw new JsonException(AccountError.EMAIL_EXIST);
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
    public boolean validResetPasswordEmailCode(String account, String code) {
        User user = getUserByAccount(account);
        if (user == null) {
            return false;
        }
        String realCode = (String) redisTemplate.opsForValue().get(RedisKeyGenerator.getUserEmailValidKey(user.getId(), user.getEmail(), MailValidateType.RESET_PASSWORD));
        return code.equalsIgnoreCase(realCode);
    }

    @Override
    public void resetPassword(String account, String code, String password) {
        final User user = getUserByAccount(account);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        String key = RedisKeyGenerator.getUserEmailValidKey(user.getId(), user.getEmail(), MailValidateType.RESET_PASSWORD);
        String record = (String) redisTemplate.opsForValue().get(key);
        if (code == null || !code.equalsIgnoreCase(record)) { throw new JsonException(AccountError.EMAIL_CODE_ERROR); }

        userRepo.modifyPassword(user.getId(), SecureUtils.getPassswd(password));
        redisTemplate.delete(key);
        tokenDao.cleanUserToken(user.getId());
    }


    @Override
    public String sendVerifyEmail(Long uid) throws MessagingException, UnsupportedEncodingException {
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
    public void verifyEmail(Long uid, String code) throws MessagingException, UnsupportedEncodingException {
        final User user = getUserById(uid);
        if (user == null) throw new JsonException(AccountError.USER_NOT_EXIST);
        if (user.getEmail() == null || user.getEmail().length() == 0) throw new JsonException(AccountError.EMAIL_NOT_SET);

        final Object record = redisTemplate.opsForValue().get(RedisKeyGenerator.getUserEmailValidKey(uid, user.getEmail(), MailValidateType.VERIFY_MAIL));
        if (!code.equals(record)) {
            throw new JsonException(AccountError.EMAIL_CODE_ERROR);
        }
    }

    @Override
    public void setEmail(Long uid, String email) {
        final User user = userRepo.getUserById(uid);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        userRepo.updateEmail(uid, email);
    }


    @Override
    public void bindEmail(Long uid, String email, String originCode, String newCode) {
        final User user = userRepo.getUserById(uid);
        if (user == null) { throw new JsonException(AccountError.USER_NOT_EXIST); }
        String originKey = null, originRecord = null, newKey = null, newRecord = null;

        // 验证原邮箱
        if (user.getEmail() != null && user.getEmail().length() != 0) {
            originKey = RedisKeyGenerator.getUserEmailValidKey(uid, user.getEmail(), MailValidateType.VERIFY_MAIL);
            originRecord = (String) redisTemplate.opsForValue().get(originKey);
            if (originCode == null || !originCode.equals(originRecord)) { throw new JsonException(AccountError.EMAIL_CODE_ERROR); }
        }

        // 验证新邮箱
        newKey = RedisKeyGenerator.getUserEmailValidKey(uid, email, MailValidateType.BIND_MAIL);
        newRecord = (String) redisTemplate.opsForValue().get(newKey);
        if (newCode == null || !newCode.equals(newRecord)) { throw new JsonException(AccountError.EMAIL_CODE_ERROR); }




        userRepo.updateEmail(uid, email);
        if (originKey != null) redisTemplate.delete(originKey);
        redisTemplate.delete(newKey);
    }


    @Override
    public String sendRegEmail(String email) {
        // 判断邮件注册开关
        if (!sysCommonConfig.getEnableEmailReg()) throw new JsonException(AccountError.EMAIL_REG_DISABLE);

        // 先判断邮箱是否已被使用
        User user = userRepo.getByEmail(email);
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
    public void grant(long uid, int type) {
        if (type > 1 || type < 0) throw new IllegalArgumentException("不合法的用户类型");
        User user = userRepo.getUserById(uid);
        if (user == null) throw new UserNoExistException(404, "用户不存在");
        if (type == UserConstants.TYPE_COMMON && "admin".equals(user.getUser())) {
            throw new IllegalArgumentException("不允许撤销admin用户的管理员权限");
        }
        userRepo.grant(uid, type);
        tokenDao.cleanUserToken(user.getId());
    }


    @Override
    public User getUserByUser(String user) throws UserNoExistException {
        return userRepo.getUserByUser(user);
    }

    @Override
    public int modifyPasswd(Long uid, String oldPassword, String newPassword) {
        User user = userRepo.getUserById(uid);
        if (user == null) throw new UserNoExistException(404, "用户不存在");
        if (!SecureUtils.getPassswd(oldPassword).equals(user.getPwd())) {
            throw new JsonException(403, "原密码错误");
        }
        return resetPasswd(uid, newPassword);
    }

    @Override
    public int resetPasswd(Long uid, String newPassword) {
        tokenDao.cleanUserToken(uid);
        return userRepo.modifyPassword(uid, SecureUtils.getPassswd(newPassword));
    }

    @Override
    public int updateLoginDate(Long uid) {

        return userRepo.updateLoginDate(uid, new Date().getTime()/1000);
    }

    @Override
    public int addUser(@Username @Valid String user, String passwd, String email, String code, boolean isEmailCode) {

        if (isEmailCode && !sysCommonConfig.getEnableEmailReg()) {

            // 请求邮箱验证，判断是否开启
            throw new JsonException(AccountError.EMAIL_REG_DISABLE);
        } else if (!isEmailCode && !sysCommonConfig.getEnableRegCode()) {

            // 请求注册邀请码验证，判断是否开启
            throw new JsonException(AccountError.REG_CODE_DISABLE);
        } else if (isEmailCode) {

            String key = RedisKeyGenerator.getRegCodeKey(email);
            // 通过邮箱验证码注册
            String recordCode = (String)redisTemplate.opsForValue().get(key);
            if (!code.equals(recordCode)) {
                throw new JsonException(AccountError.EMAIL_CODE_ERROR);
            }
            int ret = addUser(user, passwd, email, UserConstants.TYPE_COMMON);
            redisTemplate.delete(key);
            return ret;
        } else {

            // 通过注册邀请码注册
            if (!code.equals(sysCommonConfig.getRegCode())) {
                throw new JsonException(AccountError.REG_CODE_ERROR);
            }
            return addUser(user, passwd, null, UserConstants.TYPE_COMMON);
        }
    }

    @Override
    public void initAdminUser(String user, String password) {
        doAddUser(user, password, "", UserType.ADMIN);
    }

    private int doAddUser(String user, String passwd, String email, Integer type) {
        String pwd = SecureUtils.getPassswd(passwd);
        try {
            UserInfo userInfo = new com.xiaotao.saltedfishcloud.model.po.UserInfo();
            userInfo.setUser(user);
            userInfo.setPwd(pwd);
            userInfo.setEmail(email);
            userInfo.setType(type);
            userInfo.setQuota(sysCommonConfig.getDefaultQuota());
            userRepo.save(userInfo);
            redisTemplate.delete(RedisKeyGenerator.getRegCodeKey(email));
            addRegLog(userRepo.getUserById(userInfo.getId()));
            return 1;
        } catch (DuplicateKeyException e) {
            throw new JsonException(AccountError.USER_EXIST);
        }
    }

    @Override
    public int addUser(String user, String passwd, String email, Integer type) {
        String  upperName = user.toUpperCase();
        if (UserConstants.SYS_NAME_PUBLIC.equals(upperName) || UserConstants.SYS_NAME_ADMIN.equals(upperName)) {
            throw new IllegalArgumentException("用户名" + user + "为系统保留用户名，不允许添加");
        }
        if(userRepo.findByUser(user) != null) {
            throw new JsonException(AccountError.USER_EXIST);
        }
        if (email != null && !email.isEmpty() && userRepo.getByEmail(email) != null) {
            throw new JsonException(AccountError.EMAIL_EXIST);
        }
        return doAddUser(user, passwd, email, type);
    }

    private void addRegLog(User user) {
        String ip = Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map(e -> (ServletRequestAttributes) e)
                .map(ServletRequestAttributes::getRequest)
                .map(ServletRequest::getRemoteAddr)
                .orElse("unknown");

        logRecordManager.saveRecordAsync(LogRecord.builder()
                        .level(LogLevel.INFO)
                        .type("注册用户")
                        .msgAbstract("新用户[" + user.getUser() + "] 邮箱[" + user.getEmail() + "] ip[" + ip + "]")
                        .msgDetail(MapperHolder.toJsonNoEx(user))
                .build());
    }

}
