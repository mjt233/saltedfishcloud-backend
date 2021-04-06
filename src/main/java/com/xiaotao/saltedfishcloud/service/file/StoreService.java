package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import com.xiaotao.saltedfishcloud.service.file.path.RawPathHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import static com.xiaotao.saltedfishcloud.utils.FileUtils.writeFile;

/**
 * 本地文件存储服务，用于管理本地文件系统中的文件的创建，复制，删除，移动等操作
 */
@Service
@Slf4j
public class StoreService {

    /**
     * 向用户网盘目录中保存一个文件
     * @param uid   用户ID 0表示公共
     * @param input 文件输入流（该方法执行完成后会自动关闭流，不需要再次关闭）
     * @param targetDir    保存到的目标网盘目录位置（注意：不是本地真是路径）
     * @param fileInfo 文件信息
     * @return 若发生覆盖返回1 否则返回0
     * @throws HasResultException 存储文件出错
     */
    public int store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws HasResultException {
        String target = DiskConfig.getPathHandler().getStorePath(uid, targetDir, fileInfo);
        return writeFile(input, new File(target));
    }

    /**
     * 文件重命名
     * @param uid   用户ID 0表示公共
     * @param path  文件所在路径
     * @param oldName 旧文件名
     * @param newName 新文件名
     */
    public void rename(int uid, String path, String oldName, String newName) throws HasResultException {
        if ( !(DiskConfig.getPathHandler() instanceof RawPathHandler)){
            return;
        }
        String base = DiskConfig.getRawFileStoreRootPath(uid);
        File origin = new File(base + "/" + path + "/" + oldName);
        File dist = new File(base + "/" + path + "/" + newName);
        if (!origin.exists()) {
            throw new HasResultException("原文件不存在");
        }
        if (dist.exists()) {
            throw new HasResultException("文件名冲突");
        }
        if (!origin.renameTo(dist)) {
            throw new HasResultException("移动失败");
        }

    }

    /**
     * 在本地文件系统中创建文件夹
     * @param uid   用户ID
     * @param path  所在路径
     * @param name  文件夹名
     * @return 是否创建成功
     */
    public boolean mkdir(int uid, String path, String name) {
        String localFilePath = DiskConfig.getRawFileStoreRootPath(uid) + "/" + path + "/" + name;
        File file = new File(localFilePath);
        return file.mkdir();
    }

    /**
     * 删除本地文件（文件夹会连同所有子文件和目录）
     * @param uid 用户ID
     * @param path 所在路径
     * @param name 文件名
     * @return 删除的文件和文件夹总数
     */
    public long delete(int uid, String path, Collection<String> name) {
        AtomicLong cnt = new AtomicLong();
        // 本地物理基础路径
        String basePath = DiskConfig.getRawFileStoreRootPath(uid)  + "/" + path;
        name.forEach(fileName -> {

            // 本地完整路径
            String local = basePath + "/" + fileName;
            File file = new File(local);
            if (file.isDirectory()) {
                Path path1 = Paths.get(local);
                try {
                    Files.walkFileTree(path1, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            log.debug("删除文件 " + file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            log.debug("删除目录 " + dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new HasResultException(500, e.getMessage());
                }
            } else {
                if (!file.delete()){
                    log.error("文件删除失败：" + file.getPath());
                } else {
                    cnt.incrementAndGet();
                }
            }
        });
        return cnt.longValue();
    }

}
