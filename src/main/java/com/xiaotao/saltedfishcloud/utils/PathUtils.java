package com.xiaotao.saltedfishcloud.utils;


import com.xiaotao.saltedfishcloud.helper.PathBuilder;

public class PathUtils {

    /**
     * 提取一个文件的完整本地路径中 相对网盘的路径
     * 如 本地文件D:/data/xiaotao/a.jpg
     * 用户ID 233，对应用户名为xiaotao
     * 用户文件存储路径为D:/data/
     * 则返回 /a.jpg
     * @param uid       用户ID
     * @param localPath 本地路径
     * @return          相对网盘的路径
     */
    public static String getRelativePath(int uid, String localPath) {
        String local = PathBuilder.formatPath(localPath);
        String userBasePath = FileUtils.getFileStoreRootPath(uid);
        String res = local.substring(userBasePath.length());
        return res.length() == 0 ? "/" : res;
    }
}
