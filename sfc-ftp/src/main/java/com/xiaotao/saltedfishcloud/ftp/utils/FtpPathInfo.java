package com.xiaotao.saltedfishcloud.ftp.utils;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * 网盘系统的FTP路径信息<br>
 * 系统的FTP路径包含两部分：/{资源区()}/资源路径
 */
@Data
public class FtpPathInfo {
    //  资源区
    private String resourceArea = "";

    //  资源路径
    private String resourcePath;

    //  完整的FTP路径
    private String fullPath;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private PathBuilder pathBuilder = new PathBuilder();

    /**
     * @param path 完整FTP请求路径
     */
    public FtpPathInfo(String path) {
        pathBuilder.setForcePrefix(true);
        pathBuilder.append(path);
        fullPath = path = pathBuilder.toString();
        if (path.startsWith(FtpDiskType.PUBLIC)) {
            this.resourceArea = FtpDiskType.PUBLIC;
        } else if (path.startsWith(FtpDiskType.PRIVATE)) {
            this.resourceArea = FtpDiskType.PRIVATE;
        } else if(!path.equals("/")) {
            throw new IllegalArgumentException("不合法的资源区：" + path);
        }

        this.resourcePath = path.equals("/") ? null : PathBuilder.formatPath(path.substring(resourceArea.length()), true);
    }



    /**
     * 是否为FTP根目录
     * @return 若是则true，否则为false
     */
    public boolean isFtpRoot() {
        return resourceArea.isEmpty();
    }

    /**
     * 是否为资源区根目录
     * @return 若是则true，否则为false
     */
    public boolean isResourceRoot() {
        return resourcePath.equals("/");
    }

    /**
     * 是否为公共网盘区域
     */
    public boolean isPublicArea() {
        return this.resourceArea.equals(FtpDiskType.PUBLIC);
    }

    /**
     * 获取完整的原始FTP路径
     * @return 完整的原始FTP路径
     */
    public String getFullPath() {
        return fullPath;
    }

    /**
     * 取路径文件名
     * @return 文件名
     */
    public String getName() {
        return isFtpRoot() ? "" : pathBuilder.getPath().getLast();
    }

    public String getResourceParent() {
        return isResourceRoot() ? "/" : pathBuilder.range(-2, 1);
    }

    /**
     * 获取该FTP路径对应的本地文件系统物理路径
     * @param uid   用户ID
     * @return      本地文件系统物理路径
     */
    public String toNativePath(int uid) {
        if (resourceArea.equals(FtpDiskType.PUBLIC)) {
            return DiskConfig.rawPathHandler.getStorePath(0, resourcePath, null);
        } else {
            return DiskConfig.rawPathHandler.getStorePath(uid, resourcePath, null);
        }
    }

    /**
     * 获取该FTP路径对应的本地文件系统物理路径<br>
     * 如果路径是FTP根路径，则返回null
     * @param username  用户名
     * @return      本地文件系统物理路径
     */
    public String toNativePath(String username) {
        if (isFtpRoot()) {
            return null;
        }
        if (resourceArea.equals(FtpDiskType.PUBLIC)) {
            return DiskConfig.rawPathHandler.getStorePath(0, resourcePath, null);
        } else {
            return DiskConfig.getUserPrivateDiskRoot(username) + resourcePath;
        }
    }
}
