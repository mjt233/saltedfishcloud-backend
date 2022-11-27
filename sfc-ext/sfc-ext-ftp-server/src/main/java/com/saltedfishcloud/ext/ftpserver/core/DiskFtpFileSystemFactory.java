package com.saltedfishcloud.ext.ftpserver.core;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;


public class DiskFtpFileSystemFactory implements FileSystemFactory {
    @Autowired
    private UserDao userDao;

    @Autowired
    private DiskFileSystemManager fileSystemFactory;

    @Override
    public FileSystemView createFileSystemView(User user) throws FtpException {
        try {
            if (user.getName().equals(com.xiaotao.saltedfishcloud.model.po.User.SYS_NAME_PUBLIC)) {
                return new DiskFtpFileSystemView(DiskFtpUser.getAnonymousUser(), fileSystemFactory);
            }
            com.xiaotao.saltedfishcloud.model.po.User dbUser = userDao.getUserByUser(user.getName());
            if (dbUser == null) {
                throw new UserNoExistException("用户" + user + "不存在");
            }
            DiskFtpUser ftpUser = new DiskFtpUser(dbUser);
            return new DiskFtpFileSystemView(ftpUser, fileSystemFactory);
        } catch (IOException e) {
            throw new FtpException(e.getMessage());
        }
    }
}
