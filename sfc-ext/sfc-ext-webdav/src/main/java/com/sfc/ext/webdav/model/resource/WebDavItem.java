package com.sfc.ext.webdav.model.resource;

import com.sfc.ext.webdav.enums.ResourceArea;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

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
     * @param errorMessage  错误消息
     * @param uid   访问的目标用户 id
     * @param basePath 文件所在路径（不包括自己）
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
        if (uid == null || uid == User.PUBLIC_USER_ID) {
            file.setResourceArea(ResourceArea.PUBLIC);
        } else {
            file.setResourceArea(ResourceArea.PRIVATE);
        }
        return file;
    }

    /**
     * 从一个网盘文件信息 FileInfo 对象中构造
     * @param fileInfo  网盘文件信息
     * @param basePath  文件的所在目录（不包括本身）
     */
    public static WebDavItem fromFileInfo(FileInfo fileInfo, String basePath) {
        WebDavItem item = fileInfo.isDir() ? new WebDavDir() : new WebDavFile();
        item.setName(fileInfo.getName());
        item.setUid(fileInfo.getUid());
        item.setModifiedDate(fileInfo.getUpdateAt());
        item.setCreateDate(fileInfo.getCreateAt());
        item.setPath(StringUtils.appendPath(basePath, fileInfo.getName()));
        item.setResourceArea(fileInfo.getUid() == User.PUBLIC_USER_ID ? ResourceArea.PUBLIC : ResourceArea.PRIVATE);
        if (item instanceof WebDavFile f) {
            f.setContentLength(fileInfo.getSize());
            f.setContentType(FileUtils.getContentType(fileInfo.getName()));
        }
        item.setError(false);
        return item;
    }
}
