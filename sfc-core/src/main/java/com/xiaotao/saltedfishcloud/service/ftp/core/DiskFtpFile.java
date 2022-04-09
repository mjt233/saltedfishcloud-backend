package com.xiaotao.saltedfishcloud.service.ftp.core;

import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import com.xiaotao.saltedfishcloud.service.ftp.utils.FtpDiskType;
import com.xiaotao.saltedfishcloud.service.ftp.utils.FtpPathInfo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.ftplet.FtpFile;
import org.springframework.core.io.Resource;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DiskFtpFile implements FtpFile {
    private final FtpPathInfo pathInfo;
    private final DiskFtpUser user;
    private final DiskFileSystemProvider fileService;
    private Resource fileResource;
    private final int resourceUid;

    /**
     * 处于FTP根或资源根
     */
    @Getter
    private boolean isRoot;

    /**
     * 构造一个网盘FTP文件
     * @param path  请求的FTP路径
     * @param user  FTP用户
     */
    public DiskFtpFile(String path, DiskFtpUser user, DiskFileSystemProvider fileService) {
        this.user = user;
        pathInfo = new FtpPathInfo(path);
        this.fileService = fileService;
        resourceUid = pathInfo.isPublicArea() ? User.getPublicUser().getId() : user.getId();
        if (pathInfo.isFtpRoot() || pathInfo.isResourceRoot()) {
            isRoot = true;
            return;
        }
        try {
            fileResource = fileService.getFileSystem().getResource(resourceUid, pathInfo.getResourceParent(), pathInfo.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public String getAbsolutePath() {
        return pathInfo.getFullPath();
    }

    @Override
    public String getName() {
        return pathInfo.getName();
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        if (pathInfo.isFtpRoot() || pathInfo.isResourceRoot() || fileResource == null) {
            return true;
        } else {
            return fileService.getFileSystem().exist(resourceUid, pathInfo.getResourcePath()) && fileResource == null;
        }
    }

    @Override
    public boolean isFile() {
        return !isDirectory();
    }

    @Override
    public boolean doesExist() {
        return isRoot || fileService.getFileSystem().exist(resourceUid, pathInfo.getResourcePath());
    }

    @Override
    public boolean isReadable() {
        return true;
    }

    @Override
    public boolean isWritable() {
        if (SysRuntimeConfig.getInstance().getProtectModeLevel() == ProtectLevel.DATA_MOVING) {
            return false;
        }
        // FTP根目录不可写
        if (pathInfo.isFtpRoot()) {
            log.debug("根目录写入拒绝");
            return false;
        }
        // 公共网盘只允许管理员写入，其他情况均可写入
        if (pathInfo.isPublicArea() && !user.isAdmin()) {
            log.debug("公共网盘目录写入拒绝");
            return false;
        }
        return true;
    }

    @Override
    public boolean isRemovable() {
        return isWritable() && !pathInfo.isResourceRoot();
    }

    @Override
    public String getOwnerName() {
        return pathInfo.isFtpRoot() ? User.SYS_NAME_PUBLIC : user.getName();
    }

    @Override
    public String getGroupName() {
        return pathInfo.isFtpRoot() ? User.SYS_GROUP_NAME_PUBLIC : user.getName();
    }

    @Override
    public int getLinkCount() {
        return 0;
    }

    /**
     * @TODO 针对目录进行日期处理
     */
    @Override
    public long getLastModified() {
        if (isRoot() || fileResource == null) {
            return System.currentTimeMillis();
        } else {
            try {
                return fileResource.lastModified();
            } catch (IOException e) {
                e.printStackTrace();
                return System.currentTimeMillis();
            }
        }
    }

    @Override
    public boolean setLastModified(long time) {
        return false;
    }

    @Override
    public long getSize() {
        try {
            if (fileResource == null) {
                return 0;
            } else {
                return fileResource.contentLength();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public Object getPhysicalFile() {
        return null;
    }

    @Override
    public boolean mkdir() {
        PathBuilder pb = new PathBuilder();
        pb.append(pathInfo.getResourcePath());
        try {
            fileService.getFileSystem().mkdir(
                    resourceUid,
                    new PathBuilder().append(pathInfo.getResourcePath()).range(-1),
                    pathInfo.getName()
            );
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public boolean delete() {
        try {
            fileService.getFileSystem().deleteFile(
                    resourceUid,
                    (new PathBuilder()).append(pathInfo.getResourcePath()).range(-1),
                    Collections.singletonList(pathInfo.getName())
            );
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 对文件进行移动或重命名，暂不支持跨资源区操作
     * @param destination 目标文件信息
     * @return 成功与否
     */
    @Override
    public boolean move(FtpFile destination) {
        FtpPathInfo pathInfo = new FtpPathInfo(destination.getAbsolutePath());

        // 不支持跨资源区操作
        if (!pathInfo.getResourceArea().equals(this.pathInfo.getResourceArea())) {
            return false;
        }
        try {
            // 资源路径相同表示重命名
            if (pathInfo.getResourceParent().equals(this.pathInfo.getResourceParent()) ) {
                fileService.getFileSystem().rename(resourceUid, pathInfo.getResourceParent(), this.pathInfo.getName(), destination.getName());
                return true;
            } else {
                fileService.getFileSystem().move(resourceUid, this.pathInfo.getResourceParent(), pathInfo.getResourceParent(), destination.getName(), true);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<? extends FtpFile> listFiles() {
        if (pathInfo.isFtpRoot()) {
            List<DiskFtpFile> res = new LinkedList<>();
            res.add(new DiskFtpFile(FtpDiskType.PUBLIC, user, fileService));
            if (!user.isAnonymousUser()) {
                res.add(new DiskFtpFile(FtpDiskType.PRIVATE, user, fileService));
            }
            return res;
        }

        try {
            List<FileInfo>[] userFileList = fileService.getFileSystem().getUserFileList(resourceUid, pathInfo.getResourcePath());
            if (userFileList == null) {
                return Collections.emptyList();
            }
            String path = getAbsolutePath();
            List<FileInfo> res = new ArrayList<>();
            res.addAll(userFileList[0]);
            res.addAll(userFileList[1]);
            return res.stream().map(f -> new DiskFtpFile(path + "/" + f.getName(), user, fileService)).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * 创建文件输出流，创建之前会删除原文件，并设置接收文件的临时位置<br>
     *     临时位置：临时文件夹/(uid+md5(FTP文件完整路径))
     */
    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        log.debug("create output stream");
        if (doesExist()) {
            fileService.getFileSystem().deleteFile(
                    resourceUid,
                    pathInfo.getResourceParent(),
                    Collections.singletonList(pathInfo.getName())
            );
        }
        String tmpDir = System.getProperty("java.io.tmpdir");

        String tag = resourceUid + SecureUtils.getMd5(pathInfo.getFullPath());
        if (offset > 0) {
            throw new IOException("Not support random write");
        }
        return new FileOutputStream(new File(tmpDir + File.separator + tag));
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        InputStream inputStream = fileResource.getInputStream();
        if (inputStream.skip(offset) != offset) {
            throw new IOException("Out of offset");
        }
        return inputStream;
    }
}
