package com.sfc.archive.utils;

public class ArchiveUtils {
    public static boolean isDirectory(String full) {
        return full.endsWith("/");
    }
    public static String getFilename(String full) {
        String res;
        int pos = full.lastIndexOf('/');
        if (pos == -1) {

            // 不存在/，则说明是根目录下的文件（如：富婆通讯录.xlsx）
            res = full;
        } else if (pos == full.length() - 1){

            // /在末尾，是个文件夹，找第二个/出现的位置以截取文件名
            int p2 = full.lastIndexOf('/', full.length() - 2);
            if (p2 == -1) {

                // 找不到第二个/说明是个在根目录下的文件夹（如：新建文件夹/）
                res = full.substring(0, full.length() - 1);
            } else {

                // 找到了（如：新建文件夹/子文件夹/
                res = full.substring(p2 + 1, full.length() - 1);
            }
        } else {

            // 存在/但不是在末尾，普通的文件，如（文件夹/文档.doc）
            res = full.substring(pos + 1);
        }
        return res;
    }
}
