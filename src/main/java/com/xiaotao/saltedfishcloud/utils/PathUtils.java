package com.xiaotao.saltedfishcloud.utils;


import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;

import java.util.ArrayList;
import java.util.List;

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
        String userBasePath = DiskConfig.getRawFileStoreRootPath(uid);
        String res = local.substring(userBasePath.length());
        return res.length() == 0 ? "/" : res;
    }

    /**
     * 获取该路径途径的所有节点的完整路径
     * @param path  路径
     * @return  所有路径
     */
    public static String[] getAllNode(String path) {
        List<Integer> pos = new ArrayList<>();
        int l = path.length();
        for (int i = 0; i < l; i++) {
            if (path.charAt(i) == '/' && i != 0) {
                pos.add(i);
            }
        }
        int pl = pos.size();
        if (pl == 0) {
            return new String[]{path};
        }
        String[] res = new String[pl + 1];
        for (int i = 0; i < pl; i++) {
            res[i] = path.substring(0, pos.get(i));
        }
        res[res.length - 1] = path;
        return res;
    }
}
