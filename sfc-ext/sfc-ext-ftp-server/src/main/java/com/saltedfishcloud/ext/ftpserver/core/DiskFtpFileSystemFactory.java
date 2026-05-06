package com.saltedfishcloud.ext.ftpserver.core;

import com.xiaotao.saltedfishcloud.dao.jpa.UserRepo;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
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
    private UserRepo userRepo;

    @Autowired
    private DiskFileSystemManager fileSystemFactory;

    @Override
    public FileSystemView createFileSystemView(User user) throws FtpException {
        try {
            if (user.getName().equals(UserConstants.SYS_NAME_PUBLIC)) {
                return new DiskFtpFileSystemView(DiskFtpUser.getAnonymousUser(), fileSystemFactory);
            }
            com.xiaotao.saltedfishcloud.model.po.User dbUser = userRepo.getUserByUser(user.getName());
            if (dbUser == null) {
                throw new UserNoExistException("用户" + user + "不存在");
            }
            DiskFtpUser ftpUser = new DiskFtpUser(UserPrincipal.from(dbUser));
            return new DiskFtpFileSystemView(ftpUser, fileSystemFactory);
        } catch (IOException e) {
            throw new FtpException(e.getMessage());
        }
    }
}
