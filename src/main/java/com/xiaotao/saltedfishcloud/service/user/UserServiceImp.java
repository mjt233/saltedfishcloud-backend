package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.var;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import static com.xiaotao.saltedfishcloud.config.DiskConfig.ACCEPT_AVATAR_TYPE;

@Service
@Transactional(rollbackFor = Exception.class)
public class UserServiceImp implements UserService{

    @Override
    public void grant(int uid, int type) {
        if (type > 1 || type < 0) throw new IllegalArgumentException("不合法的用户类型");
        User admin = userDao.getUserById(uid);
        if (admin != null && type == User.TYPE_COMMON && "admin".equals(admin.getUsername())) {
            throw new IllegalArgumentException("不允许撤销admin用户的管理员权限");
        }
        int res = userDao.grant(uid, type);
        if (res == 0) {
            throw new UserNoExistException(404, "用户不存在");
        }
    }

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
    public int modifyPasswd(Integer uid, String oldPassword, String newPassword) {
        User user = userDao.getUserById(uid);
        if (user == null) throw new UserNoExistException(404, "用户不存在");
        if (!SecureUtils.getPassswd(oldPassword).equals(user.getPwd())) {
            throw new JsonException(403, "原密码错误");
        }
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
            var res = userDao.addUser(user, pwd, type);
            Files.createDirectory(Paths.get(DiskConfig.getUserPrivateDiskRoot(user)));
            return res;
        } catch (DuplicateKeyException e) {
            throw new JsonException(400, "用户" + user + "已被注册");
        } catch (IOException e) {
            e.printStackTrace();
            throw new JsonException(500, "空间初始化失败");
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
