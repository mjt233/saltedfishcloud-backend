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
