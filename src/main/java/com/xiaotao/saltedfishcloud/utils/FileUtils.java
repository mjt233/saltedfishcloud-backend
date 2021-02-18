package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.po.DirCollection;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

public class FileUtils {
    private static final HashMap<String,String> map = new HashMap<>();
    static {
        // 一般网页资源
        map.put("html", "text/html;charset=utf-8");
        map.put("htm", "text/html;charset=utf-8");
        map.put("js", "application/x-javascript;charset=utf-8");
        map.put("css", "text/css;charset=utf-8");
        map.put("txt", "text/plain;charset=utf-8");

        // 图片
        map.put("gif", "image/gif");
        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("png", "image/png");
        map.put("ico", "image/x-icon");

        // 音乐
        map.put("mp3", "audio/mp3");
        map.put("mp2", "audio/mp2");
        map.put("ogg", "audio/ogg");
        map.put("ape", "audio/ape");
        map.put("flac", "audio/flac");

        // 视频
        map.put("mp4", "video/mp4");

        // 视频
        map.put("pdf", "application/pdf");
    }

    static public String getContentType(String name) {
        name = getSuffix(name);
        String res = map.get(name);
        return res == null ? "application/octet-stream" : res;
    }

    /**
     * 搜索遍历目录，取出文件夹下的所有文件和目录
     * @param path 本地文件夹路径
     * @return DirCollection对象
     */
    static public DirCollection scanDir(String path) throws IOException {
        DirCollection res = new DirCollection();
        Path root = Paths.get(path);
        Files.walkFileTree(root , new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                res.addFile(file.toFile());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!Files.isSameFile(dir, root)) {
                    res.addFile(dir.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return res;
    }


    /**
     * 获取文件名中的后缀名，即最后一个字符'.'后面的子字符串，若无后缀名，则返回整个原文件名
     * @author xiaotao
     * @param name  文件名或文件路径
     * @return  后缀名，不带'.'
     */
    public static String getSuffix(String name) {
        String[] split = name.split("\\.");
        if (split.length != 0) {
            return split[split.length - 1].toLowerCase();
        } else {
            return name;
        }
    }

    /**
     * 通过UID获取文件存储的用户根目录，公共用户使用DiskConfig.PUBLIC_ROOT 其他用户使用DiskConfig.PRIVATE_ROOT + "/" + {username}
     * @param uid 用户ID 0表示公共
     * @return 本地文件存储用户根目录，末尾不带/
     */
    static public String getFileStoreRootPath(int uid) {
        return uid == 0 ? DiskConfig.PUBLIC_ROOT : DiskConfig.getUserPrivatePath();
    }
}
