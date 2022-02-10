package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class DiskFileUtils {
    /**
     * 提取一个文件的完整本地路径中 相对网盘的路径<br>
     * 如 本地文件D:/data/xiaotao/a.jpg <br>
     * 用户ID 233，对应用户名为xiaotao <br>
     * 用户文件存储路径为D:/data/ <br>
     * 则返回 /a.jpg <br>
     * @param user       用户信息
     * @param localPath 本地路径
     * @return          相对网盘的路径
     */
    public static String getRelativePath(User user, String localPath) {
        String local = PathBuilder.formatPath(localPath);
        String userBasePath;
        String res;
        if (user.getId() == 0) {
            userBasePath = LocalStoreConfig.getRawFileStoreRootPath(0);
        } else {
            userBasePath = LocalStoreConfig.getUserPrivateDiskRoot(user.getUser());
        }
        res = local.substring(userBasePath.length());
        return res.length() == 0 ? "/" : res;
    }

    /**
     * 依据文件MD5，从存储仓库中删除对应的文件（仅Unique模式下有效）
     * @param md5   文件MD5
     * @return 删除的文件数+目录数
     */
    public static int delete(String md5) throws IOException {
        int res = 1;
        Path filePath = Paths.get(LocalStoreConfig.getUniqueStoreRoot() + "/" + StringUtils.getUniquePath(md5));
        Files.delete(filePath);
        log.debug("删除本地文件：{}", filePath);
        DirectoryStream<Path> paths = Files.newDirectoryStream(filePath.getParent());
        // 最里层目录
        if (  !paths.iterator().hasNext() ) {
            log.debug("删除本地目录：{}", filePath.getParent());
            res++;
            paths.close();
            Files.delete(filePath.getParent());
            paths = Files.newDirectoryStream(filePath.getParent().getParent());

            // 外层目录
            if ( !paths.iterator().hasNext()) {
                log.debug("删除本地目录：{}", filePath.getParent().getParent());
                res++;
                Files.delete(filePath.getParent().getParent());
                paths.close();
            }
            paths.close();
        } else {
            paths.close();
        }
        return res;
    }
}
