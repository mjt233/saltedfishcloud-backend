package com.sfc.ext.webdav.model.resource;

import com.sfc.ext.webdav.enums.ResourceArea;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;
import java.util.Optional;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class WebDavItem {
    private String name;

    private Date createDate;

    private Date modifiedDate;

    private Long uid;

    private ResourceArea resourceArea;

    /**
     * 文件在WebDAV文件系统中，排除掉资源区域前缀后的完整网盘路径。<br>
     * 如：WebDAV路径/private/backups/photos 对应的网盘路径为 /backups/photos，则path也为 /backups/photos
     */
    private String path;

    /**
     * 是否为错误消息项
     */
    private boolean isError;

    /**
     * 是否为虚拟根目录
     */
    private boolean isVirtualRoot;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 从一个错误消息构造一个虚拟的文件
     *
     * @param errorMessage 错误消息
     * @param uid          访问的目标用户 id
     * @param basePath     文件所在路径（不包括自己）
     */
    public static WebDavItem fromError(String errorMessage, Long uid, String basePath) {
        WebDavFile file = new WebDavFile();
        String msg = String.valueOf(errorMessage);
        String errorFileName = "系统出错.txt";
        Date now = new Date();
        file.setName(errorFileName);
        file.setPath(StringUtils.appendPath(basePath, errorFileName));
        file.setErrorMessage(msg);
        file.setContentLength((long) msg.length());
        file.setUid(uid);
        file.setModifiedDate(now);
        file.setCreateDate(now);
        file.setError(true);
        if (uid == null || uid == UserConstants.PUBLIC_USER_ID) {
            file.setResourceArea(ResourceArea.PUBLIC);
        } else {
            file.setResourceArea(ResourceArea.PRIVATE);
        }
        return file;
    }

    /**
     * 从一个网盘文件信息 FileInfo 对象中构造
     *
     * @param fileInfo 网盘文件信息
     * @param basePath 文件的所在目录（不包括本身）
     */
    public static WebDavItem fromFileInfo(FileInfo fileInfo, String basePath) {
        WebDavItem item = fileInfo.isDir() ? new WebDavDir() : new WebDavFile();
        item.setName(fileInfo.getName());
        item.setUid(fileInfo.getUid());
        item.setModifiedDate(
                Optional.ofNullable(fileInfo.getMtime())
                        .or(() -> Optional.ofNullable(fileInfo.getCtime()))
                        .map(Date::new)
                        .or(() -> Optional.ofNullable(fileInfo.getUpdateAt()))
                        .or(() -> Optional.ofNullable(fileInfo.getCreateAt()))
                        .orElse(new Date())
        );
        item.setCreateDate(
                Optional.ofNullable(fileInfo.getCtime())
                        .or(() -> Optional.ofNullable(fileInfo.getMtime()))
                        .map(Date::new)
                        .or(() -> Optional.ofNullable(fileInfo.getCreateAt()))
                        .or(() -> Optional.ofNullable(fileInfo.getUpdateAt()))
                        .orElse(new Date())
        );
        item.setPath(StringUtils.appendPath(basePath, fileInfo.getName()));
        item.setResourceArea(fileInfo.getUid() == UserConstants.PUBLIC_USER_ID ? ResourceArea.PUBLIC : ResourceArea.PRIVATE);
        if (item instanceof WebDavFile f) {
            f.setContentLength(fileInfo.getSize());
            f.setContentType(FileUtils.getContentType(fileInfo.getName()));
        }
        item.setError(false);
        return item;
    }
}
