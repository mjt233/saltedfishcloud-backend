package com.saltedfishcloud.ext.ftpserver.core;

import com.saltedfishcloud.ext.ftpserver.utils.FtpDiskType;
import com.saltedfishcloud.ext.ftpserver.utils.FtpPathInfo;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.ftplet.FtpFile;
import org.springframework.core.io.Resource;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DiskFtpFile implements FtpFile {
    private final FtpPathInfo pathInfo;
    private final DiskFtpUser user;
    private final DiskFileSystemManager fileService;
    private Resource fileResource;
    private final long resourceUid;

    private Long lastModifiedValue;

    /**
     * 处于FTP根或资源根
     */
    @Getter
    private boolean isRoot;

    private boolean noResource;

    @Setter
    private Boolean isDir;

    @Setter
    private Boolean isExist;

    /**
     * 构造一个网盘FTP文件
     * @param path  请求的FTP路径
     * @param user  FTP用户
     */
    public DiskFtpFile(String path, DiskFtpUser user, DiskFileSystemManager fileService) {
        this.user = user;
        pathInfo = new FtpPathInfo(path);
        this.fileService = fileService;
        resourceUid = pathInfo.isPublicArea() ? User.getPublicUser().getId() : user.getId();
        if (pathInfo.isFtpRoot() || pathInfo.isResourceRoot()) {
            isRoot = true;
        }
    }
    protected Resource getFileResource() {
        if (fileResource != null) {
            return fileResource;
        } else if (noResource) {
            return null;
        } else {
            try {
                fileResource = fileService.getMainFileSystem().getResource(resourceUid, pathInfo.getResourceParent(), pathInfo.getName());
                return fileResource;
            } catch (IOException e) {
                noResource = true;
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * 用来与{@link #setLastModified(long)}进行区分，仅设置更改日期的值到对象中从而影响{@link #getLastModified()}的结果，不影响系统数据。<br>
     * 因为按照接口定义，{@link #setLastModified(long)}会修改文件的修改日期。
     * @param lastModifiedValue 修改日期
     */
    public void setLastModifiedValue(Long lastModifiedValue) {
        this.lastModifiedValue = lastModifiedValue;
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
        if (isDir != null) {
            return isDir;
        }

        if (pathInfo.isFtpRoot() || pathInfo.isResourceRoot() || getFileResource() == null) {
            isDir = true;
        } else {
            try {
                isDir = fileService.getMainFileSystem().exist(resourceUid, pathInfo.getResourcePath()) && getFileResource() == null;
            } catch (IOException e) {
                log.error("[FTP Server]判断目录类型失败：", e);
                return false;
            }
        }
        return isDir;
    }

    @Override
    public boolean isFile() {
        return !isDirectory();
    }

    @Override
    public boolean doesExist() {
        if (isExist != null) {
            return isExist;
        }
        try {
            isExist = isRoot || fileService.getMainFileSystem().exist(resourceUid, pathInfo.getResourcePath());
        } catch (IOException e) {
            log.error("[FTP Server]判断文件是否存在失败：", e);
            return false;
        }
        return isExist;
    }

    @Override
    public boolean isReadable() {
        return true;
    }

    @Override
    public boolean isWritable() {
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
     * todo 针对目录进行日期处理
     */
    @Override
    public long getLastModified() {
        if(lastModifiedValue != null) {
            return lastModifiedValue;
        }
        Resource fileResource = getFileResource();
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
            Resource fileResource = getFileResource();
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
            fileService.getMainFileSystem().mkdir(
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
            fileService.getMainFileSystem().deleteFile(
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
                fileService.getMainFileSystem().rename(resourceUid, pathInfo.getResourceParent(), this.pathInfo.getName(), destination.getName());
                return true;
            } else {
                fileService.getMainFileSystem().move(resourceUid, this.pathInfo.getResourceParent(), pathInfo.getResourceParent(), destination.getName(), true);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    protected List<? extends FtpFile> fileInfo2FtpFile(List<FileInfo> fileInfos) {
        return fileInfos.stream().map(fileInfo -> {
            String path = getAbsolutePath();
            DiskFtpFile ftpFile = new DiskFtpFile(path + "/" + fileInfo.getName(), user, fileService);
            ftpFile.isDir = fileInfo.isDir();
            ftpFile.isExist = true;
            ftpFile.setLastModifiedValue(Optional
                    .ofNullable(fileInfo.getLastModified())
                    .orElseGet(() -> Optional
                            .ofNullable(fileInfo.getCreatedAt())
                            .map(Date::getTime)
                            .orElse(System.currentTimeMillis())
                    )
            );
            return ftpFile;
        }).collect(Collectors.toList());
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
            List<FileInfo>[] userFileList = fileService.getMainFileSystem().getUserFileList(resourceUid, pathInfo.getResourcePath());
            if (userFileList == null) {
                return Collections.emptyList();
            }
            List<FtpFile> res = new ArrayList<>();
            if (userFileList[0] != null) {
                List<? extends FtpFile> ftpFiles = fileInfo2FtpFile(userFileList[0]);
                res.addAll(ftpFiles);
            }
            if (userFileList[1] != null) {
                List<? extends FtpFile> ftpFiles = fileInfo2FtpFile(userFileList[1]);
                res.addAll(ftpFiles);
            }
            return res;
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
            fileService.getMainFileSystem().deleteFile(
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
        return new FileOutputStream(tmpDir + File.separator + tag);
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        InputStream inputStream = getFileResource().getInputStream();
        if (inputStream.skip(offset) != offset) {
            throw new IOException("Out of offset");
        }
        return inputStream;
    }
}
