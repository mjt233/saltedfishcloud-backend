package com.xiaotao.saltedfishcloud.service.ftp;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.ftp.utils.FtpPathInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class DiskFtpFileSystemView implements FileSystemView {
    private final PathBuilder pathBuilder = new PathBuilder();
    private final DiskFtpUser user;
    private final DiskFileSystemFactory fileSystemFactory;
    public DiskFtpFileSystemView(DiskFtpUser user, DiskFileSystemFactory fileSystemFactory) throws IOException {
        this.user = user;
        pathBuilder.setForcePrefix(true);
        if (!user.isAnonymousUser()) {
            Path up = Paths.get(DiskConfig.getUserPrivateDiskRoot(user.getName()));
            if (!Files.exists(up)) {
                Files.createDirectory(up);
            }
        }
        this.fileSystemFactory = fileSystemFactory;
    }

    @Override
    public FtpFile getHomeDirectory() {
        return new DiskFtpFile("/", user, fileSystemFactory);
    }

    @Override
    public FtpFile getWorkingDirectory() {
        log.debug("取cwd:" + pathBuilder.toString());
        DiskFtpFile file;
        try {
            file = new DiskFtpFile(pathBuilder.toString(), user, fileSystemFactory);
        } catch (IllegalArgumentException e) {
            changeWorkingDirectory("/");
            return new DiskFtpFile("/", user, fileSystemFactory);
        }
        return file;
    }

    @Override
    public boolean changeWorkingDirectory(String dir) {
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
    public FtpFile getFile(String file) {
        String path;
        if (file.startsWith("/")) {
            path = file;
        } else {
            path = pathBuilder.toString() + "/" +file;
        }
        return new DiskFtpFile(path, user, fileSystemFactory);
    }

    @Override
    public boolean isRandomAccessible() {
        return false;
    }

    @Override
    public void dispose() {

    }
}
