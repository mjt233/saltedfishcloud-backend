package com.xiaotao.saltedfishcloud.service.ftp.core;

import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.exception.UserNoExistException;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import lombok.RequiredArgsConstructor;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DiskFtpFileSystemFactory implements FileSystemFactory {
    private final UserDao userDao;
    private final DiskFileSystemFactory fileSystemFactory;
    @Override
    public FileSystemView createFileSystemView(User user) throws FtpException {
        try {
            if (user.getName().equals(com.xiaotao.saltedfishcloud.entity.po.User.SYS_NAME_PUBLIC)) {
                return new DiskFtpFileSystemView(DiskFtpUser.getAnonymousUser(), fileSystemFactory);
            }
            com.xiaotao.saltedfishcloud.entity.po.User dbUser = userDao.getUserByUser(user.getName());
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
