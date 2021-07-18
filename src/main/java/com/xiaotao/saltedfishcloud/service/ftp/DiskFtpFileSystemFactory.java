package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

@Component
public class DiskFtpFileSystemFactory implements FileSystemFactory {
    private final UserDao userDao;
    public DiskFtpFileSystemFactory(UserDao userDao) {
        this.userDao = userDao;
    }
    @Override
    public FileSystemView createFileSystemView(User user) throws FtpException {
        try {
            if (user.getName().equals("anonymous")) {
                return new DiskFtpFileSystemView(DiskFtpUser.getAnonymousUser());
            }
            com.xiaotao.saltedfishcloud.po.User dbUser = userDao.getUserByUser(user.getName());
            if (dbUser == null) {
                throw new UserNoExistException("用户" + user + "不存在");
            }
            DiskFtpUser ftpUser = new DiskFtpUser(dbUser);
            return new DiskFtpFileSystemView(ftpUser);
        } catch (IOException e) {
            throw new FtpException(e.getMessage());
        }
    }
}
