package com.xiaotao.saltedfishcloud.service.file.path;

import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;

public interface PathHandler {
    /**
     * 获取文件在本地文件系统中的完整存储路径
     * @param uid       用户ID 0表示公共
     * @param targetDir 请求的目标目录（是相对用户网盘根目录的目录）
     * @param fileInfo  目标文件信息，若为null，则表示目标目录本身的本地存储路径
     * @return 路径
     */
    String getStorePath(int uid, String targetDir,BasicFileInfo fileInfo);
}
