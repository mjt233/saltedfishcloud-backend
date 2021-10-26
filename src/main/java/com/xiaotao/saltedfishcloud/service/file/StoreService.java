package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnableOverwriteException;
import com.xiaotao.saltedfishcloud.po.file.BasicFileInfo;
import com.xiaotao.saltedfishcloud.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.exception.DirectoryAlreadyExistsException;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地文件存储服务，用于管理本地文件系统中的文件的创建，复制，删除，移动等操作
 */
@Service
@Slf4j
public class StoreService implements LocalStoreService {
    @Override
    public void moveToSave(int uid, Path nativePath, String diskPath, BasicFileInfo fileInfo) throws IOException {
        Path sourcePath = nativePath; // 本地源文件
        Path targetPath = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, diskPath, fileInfo)); // 被移动到的目标位置
        if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
            // 唯一文件仓库中的路径
            sourcePath = Paths.get(DiskConfig.uniquePathHandler.getStorePath(uid, diskPath, fileInfo)); // 文件仓库源文件路径
            if (Files.exists(sourcePath)) {
                // 已存在相同文件时，直接删除本地文件
                log.debug("file md5 HIT: {}", fileInfo.getMd5());
                Files.delete(nativePath);
                if (Files.exists(targetPath)) {
                    Files.delete(targetPath);
                }
            } else {
                // 将本地文件移动到唯一仓库
                log.debug("file md5 NOT HIT: {}", fileInfo.getMd5());
                FileUtils.createParentDirectory(sourcePath);
                Files.move(nativePath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
            }
            // 在目标网盘位置创建文件仓库中的文件链接
            log.debug("Create file link: {} <==> {}", targetPath, sourcePath);
            Files.createLink(targetPath, sourcePath);
        } else {
            // 非唯一模式，直接将文件移动到目标位置
            if (!sourcePath.equals(targetPath)) {
                log.debug("File move {} => {}", sourcePath, targetPath);
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @Override
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, Boolean overwrite) throws IOException {
        BasicFileInfo fileInfo = new BasicFileInfo(sourceName, null);
        String localSource = DiskConfig.getPathHandler().getStorePath(uid, source, null);
        String localTarget = DiskConfig.getPathHandler().getStorePath(targetId, target, null);

        boolean useHardLink = DiskConfig.STORE_TYPE == StoreType.UNIQUE;
        FileUtils.copy(Paths.get(localSource),Paths.get(localTarget), sourceName, targetName, useHardLink);
    }

    @Override
    public void store(int uid, InputStream input, String targetDir, FileInfo fileInfo) throws JsonException, IOException {
        Path md5Target = Paths.get(DiskConfig.uniquePathHandler.getStorePath(uid, targetDir, fileInfo));
        if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
            if (Files.exists(md5Target)) {
                log.debug("file md5 HIT:" + fileInfo.getMd5());
                if (Files.size(md5Target) != fileInfo.getSize()) {
                    throw new DuplicateKeyException("文件MD5冲突");
                }
            } else {
                log.debug("file md5 NOT HIT, saving:" + fileInfo.getMd5());
                FileUtils.createParentDirectory(md5Target);
                Files.copy(input, md5Target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path rawTarget = Paths.get(DiskConfig.rawPathHandler.getStorePath(uid, targetDir, fileInfo));
        if (Files.exists(rawTarget) && Files.isDirectory(rawTarget)) {
            throw new UnableOverwriteException(409, "已存在同名目录: " + targetDir + "/" + fileInfo.getName());
        }
        FileUtils.createParentDirectory(rawTarget);
        if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
            log.info("create hard link:" + md5Target + " <==> "  + rawTarget);
            if (Files.exists(rawTarget)) Files.delete(rawTarget);
            Files.createLink(rawTarget, md5Target);
        } else {
            log.info("save file:" + rawTarget);
            Files.copy(input, rawTarget, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void move(int uid, String source, String target, String name, boolean overwrite) throws IOException {
        PathHandler pathHandler = DiskConfig.getPathHandler();
        BasicFileInfo fileInfo = new BasicFileInfo(name, null);
        Path sourcePath = Paths.get(pathHandler.getStorePath(uid, source, fileInfo));
        Path targetPath = Paths.get(pathHandler.getStorePath(uid, target, fileInfo));
        if (Files.exists(targetPath)) {
            if (Files.isDirectory(sourcePath) != Files.isDirectory(targetPath)) {
                throw new UnsupportedOperationException("文件类型不一致，无法移动");
            }

            if (Files.isDirectory(sourcePath)) {
                // 目录则合并
                FileUtils.mergeDir(sourcePath.toString(), targetPath.toString(), overwrite);
            } else if (overwrite){
                // 文件则替换移动（仅当overwrite为true时）
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // 为了与数据库记录保持一致，原文件还是要删滴
                Files.delete(sourcePath);
            }
        } else {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void rename(int uid, String path, String oldName, String newName) throws JsonException {
        String base = DiskConfig.getRawFileStoreRootPath(uid);
        File origin = new File(base + "/" + path + "/" + oldName);
        File dist = new File(base + "/" + path + "/" + newName);
        if (!origin.exists()) {
            throw new JsonException("原文件不存在");
        }
        if (dist.exists()) {
            throw new JsonException("文件名冲突");
        }
        if (!origin.renameTo(dist)) {
            throw new JsonException("移动失败");
        }

    }

    @Override
    public boolean mkdir(int uid, String path, String name) throws FileAlreadyExistsException, DirectoryAlreadyExistsException {
        String localFilePath = DiskConfig.getRawFileStoreRootPath(uid) + "/" + path + "/" + name;
        File file = new File(localFilePath);
        if (file.mkdir()) {
            return true;
        } else {
            if (file.exists()) {
                if (file.isDirectory()) {
                    throw new DirectoryAlreadyExistsException(file + "/" + name);
                } else {
                    throw new FileAlreadyExistsException(file + "/" + name);
                }
            }
            log.error("在本地路径\"" + localFilePath + "\"创建文件夹失败");
            return false;
        }
    }

    @Override
    public int delete(String md5) throws IOException {
        int res = 1;
        Path filePath = Paths.get(DiskConfig.getUniqueStoreRoot() + "/" + StringUtils.getUniquePath(md5));
        Files.delete(filePath);
        log.debug("删除本地文件：" + filePath);
        DirectoryStream<Path> paths = Files.newDirectoryStream(filePath.getParent());
        // 最里层目录
        if (  !paths.iterator().hasNext() ) {
            log.debug("删除本地目录：" + filePath.getParent());
            res++;
            paths.close();
            Files.delete(filePath.getParent());
            paths = Files.newDirectoryStream(filePath.getParent().getParent());

            // 外层目录
            if ( !paths.iterator().hasNext()) {
                log.debug("删除本地目录：" + filePath.getParent().getParent());
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

    @Override
    public long delete(int uid, String path, Collection<String> files) {
        AtomicLong cnt = new AtomicLong();
        // 本地物理基础路径
        String basePath = DiskConfig.getRawFileStoreRootPath(uid)  + "/" + path;
        files.forEach(fileName -> {

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
                    throw new JsonException(500, e.getMessage());
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
