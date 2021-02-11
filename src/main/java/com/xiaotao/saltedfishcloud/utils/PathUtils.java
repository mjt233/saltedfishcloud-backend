package com.xiaotao.saltedfishcloud.utils;


import com.xiaotao.saltedfishcloud.helper.PathBuilder;

public class PathUtils {

    /**
     * 提取一个文件的完整本地路径中相对网盘的路径
     * @param uid       用户ID
     * @param localPath 本地路径
     * @return          相对网盘的路径
     */
    public static String getRelativePath(int uid, String localPath) {
        PathBuilder pb = new PathBuilder();
        String local = pb.append(localPath).toString();
        String userBasePath = FileUtils.getFileStoreRootPath(uid);
        String res = local.substring(userBasePath.length());
        return res.length() == 0 ? "/" : res;
    }
}
