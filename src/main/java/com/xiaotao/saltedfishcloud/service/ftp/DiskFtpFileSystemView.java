package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.ftp.utils.FtpPathInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;

import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
public class DiskFtpFileSystemView implements FileSystemView {
    private final PathBuilder pathBuilder = new PathBuilder();
    private final DiskFtpUser user;
    public DiskFtpFileSystemView(DiskFtpUser user) {
        this.user = user;
        pathBuilder.setForcePrefix(true);
    }

    @Override
    public FtpFile getHomeDirectory() throws FtpException {
        return new DiskFtpFile("/", user);
    }

    @Override
    public FtpFile getWorkingDirectory() throws FtpException {
        log.debug("取cwd:" + pathBuilder.toString());
        DiskFtpFile file;
        try {
            file = new DiskFtpFile(pathBuilder.toString(), user);
        } catch (IllegalArgumentException e) {
            changeWorkingDirectory("/");
            return new DiskFtpFile("/", user);
        }
        return file;
    }

    @Override
    public boolean changeWorkingDirectory(String dir) throws FtpException {
        String originalPath = pathBuilder.toString();
        try {
            if (dir.startsWith("/")) {
                // 绝对路径
                pathBuilder.update(dir);
            } else {
                // 相对路径
                pathBuilder.append(dir);
            }

            FtpPathInfo ftpPathInfo;
            ftpPathInfo = new FtpPathInfo(pathBuilder.toString());

            // cd到根
            if (ftpPathInfo.isFtpRoot()) {
                return true;
            }

            // cd到具体目录
            if(!Files.exists(Paths.get(ftpPathInfo.toNativePath(user.getName())))) {
                throw new IllegalArgumentException("路径不存在，切换到：" + originalPath);
            }

            return true;
        } catch (IllegalArgumentException e) {
            pathBuilder.update(originalPath);
            return false;
        }
    }

    @Override
    public FtpFile getFile(String file) throws FtpException {
        String path;
        if (file.startsWith("/")) {
            path = file;
        } else {
            path = pathBuilder.toString() + "/" +file;
        }
        return new DiskFtpFile(path, user);
    }

    @Override
    public boolean isRandomAccessible() throws FtpException {
        return false;
    }

    @Override
    public void dispose() {

    }
}
