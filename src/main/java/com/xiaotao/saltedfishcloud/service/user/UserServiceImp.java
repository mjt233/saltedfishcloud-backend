package com.xiaotao.saltedfishcloud.service.user;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.stereotype.Service;
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
    public int modifyPasswd(Integer uid, String oldPassword, String newPassword) {
        User user = userDao.getUserById(uid);
        if (user == null) throw new UserNoExistException(404, "用户不存在");
        if (!SecureUtils.getPassswd(oldPassword).equals(user.getPwd())) {
            throw new HasResultException(403, "原密码错误");
        }
        return userDao.modifyPassword(uid, SecureUtils.getPassswd(newPassword));
    }

    @Override
    public int updateLoginDate(Integer uid) {

        return userDao.updateLoginDate(uid, new Date().getTime()/1000);
    }

    @Override
    public int addUser(String user, String passwd, Integer type) {
        String pwd = SecureUtils.getPassswd(passwd);
        return userDao.addUser(user, pwd, type);
    }

    @Override
    public void setAvatar(String username, MultipartFile file) {

        //  文件属性约束：大小不得大于3MiB，限定文件类型
        if (file.getSize() > 1024*1024*3) {
            throw new HasResultException(400, "文件过大");
        }
        String suffix = FileUtils.getSuffix(file.getOriginalFilename());
        if ( !ACCEPT_AVATAR_TYPE.contains(suffix)) {
            throw new HasResultException(400, "不支持的格式，只支持jpg, jpeg, gif, png");
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
            throw new HasResultException(500, e.getMessage());
        }

    }
}
