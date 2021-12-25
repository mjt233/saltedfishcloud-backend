package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
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
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.MailSender;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import static com.xiaotao.saltedfishcloud.config.DiskConfig.ACCEPT_AVATAR_TYPE;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class UserServiceImp implements UserService{
    private final TokenDao tokenDao;
    private final UserDao userDao;
    private final JavaMailSender mailSender;
    private final MailMessageGenerator mailMessageGenerator;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public String sendRegEmail(String email) {

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
    public int addUser(String user, String passwd, Integer type) {
        var upperName = user.toUpperCase();
        if (User.SYS_NAME_PUBLIC.equals(upperName) || User.SYS_NAME_ADMIN.equals(upperName)) {
            throw new IllegalArgumentException("用户名" + user + "为系统保留用户名，不允许添加");
        }
        String pwd = SecureUtils.getPassswd(passwd);
        try {
            return userDao.addUser(user, pwd, type);
        } catch (DuplicateKeyException e) {
            throw new JsonException(400, "用户" + user + "已被注册");
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
