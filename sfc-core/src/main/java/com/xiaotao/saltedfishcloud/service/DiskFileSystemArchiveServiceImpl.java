package com.xiaotao.saltedfishcloud.service;

import com.sfc.archive.ArchiveManager;
import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.comporessor.ArchiveResourceEntry;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.extractor.ArchiveExtractorVisitor;
import com.sfc.archive.model.ArchiveParam;
import com.sfc.constant.error.FileSystemError;
import com.sfc.enums.ArchiveError;
import com.sfc.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipException;

/**
 * 默认的网盘文件系统压缩、解压缩服务实现类
 *
 * todo 对接异步任务系统
 */
@Service
@Slf4j
public class DiskFileSystemArchiveServiceImpl implements DiskFileSystemArchiveService {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private SysProperties sysProperties;

    @Autowired
    private ArchiveManager archiveManager;

    @Override
    public void compressAndWriteOut(int uid, String path, Collection<String> names, ArchiveType type, OutputStream outputStream) throws IOException {
        ArchiveParam archiveParam = ArchiveParam.builder()
                .encoding(sysProperties.getStore().getArchiveEncoding())
                .type("zip")
                .build();
        try(ArchiveCompressor compressor = archiveManager.getCompressor(archiveParam, outputStream)) {
            compress(uid, path, path, names, compressor);
            compressor.start();
        }
    }

    /**
     * 压缩目录下指定的文件
     * @param uid       用户ID
     * @param root      压缩根
     * @param path      路径
     * @param names     文件名集合
     * @param compressor    压缩器
     */
    private void compress(int uid, String root, String path, Collection<String> names, ArchiveCompressor compressor) throws IOException {
        String curDir = StringUtils.removePrefix(root, path).replaceAll("//+", "").replaceAll("^/+", "");
        for (String name : names) {
            Resource resource = diskFileSystemManager.getMainFileSystem().getResource(uid, path, name);
            if (resource == null) {
                compressDir(uid, root, StringUtils.appendPath(path, name), compressor, 1);
            } else {
                compressor.addFile(new ArchiveResourceEntry(
                        curDir.length() == 0 ? name : StringUtils.appendPath(curDir, name),
                        resource.contentLength(),
                        resource
                ));
            }
        }
    }

    /**
     * 压缩目标文件夹内的所有内容
     * @param uid   用户ID
     * @param root  压缩根路径
     * @param path  要压缩的完整目录路径
     * @param compressor    压缩器
     * @param depth         当前压缩深度
     */
    private void compressDir(int uid, String root, String path, ArchiveCompressor compressor, int depth) throws IOException {
        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        List<FileInfo>[] list = fileSystem.getUserFileList(uid, path);
        String curPath = StringUtils.removePrefix(root, path).replaceAll("//+", "/").replaceAll("^/+", "");
        compressor.addFile(new ArchiveResourceEntry(
                curPath + "/",
                0,
                null
        ));
        for (FileInfo file : list[1]) {
            compressor.addFile(new ArchiveResourceEntry(
                    curPath + "/" + file.getName(), file.getSize(), fileSystem.getResource(uid, path, file.getName()))
            );
        }

        for (FileInfo file : list[0]) {
            compressor.addFile(new ArchiveResourceEntry(curPath + "/" + file.getName() + "/", 0, null));
            compressDir(uid, root, path + "/" + file.getName(), compressor, depth + 1);
        }
    }

    @Override
    public void compress(int uid, String path, Collection<String> names, String dest, ArchiveType type) throws IOException {
        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        boolean exist = fileSystem.exist(uid, dest);
        if (exist && fileSystem.getResource(uid, dest, "") == null) {
            throw new JsonException(FileSystemError.RESOURCE_TYPE_NOT_MATCH);
        }
        Path temp = Paths.get(PathUtils.getTempDirectory() + "/temp_zip" + System.currentTimeMillis());
        try(OutputStream output = Files.newOutputStream(temp)) {
            PathBuilder pb = new PathBuilder();
            pb.setForcePrefix(true);
            pb.append(dest);
            compressAndWriteOut(uid, path, names, ArchiveType.ZIP, output);
            final FileInfo fileInfo = FileInfo.getLocal(temp.toString());
            fileInfo.setName(pb.range(1, -1).replace("/", ""));

            if (exist) {
                fileSystem.deleteFile(uid, PathUtils.getParentPath(dest), Collections.singletonList(PathUtils.getLastNode(dest)));
            }
            fileSystem.moveToSaveFile(uid, temp, pb.range(-1), fileInfo);
        } finally {
            Files.deleteIfExists(temp);
        }
    }


    /**
     * todo 使用任务队列控制同时进行解压缩的数量
     * todo 边解压边计算MD5
     * todo 实现实时计算MD5的IO流
     * todo 封装代码
     */
    @Override
    public void extractArchive(int uid, String path, String name, String dest) throws IOException {
        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        Resource resource = fileSystem.getResource(uid, path, name);
        if (resource == null) throw new NoSuchFileException(path + "/" + name);

        // 创建临时目录用于存放临时解压的文件
        Path tempBasePath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), System.currentTimeMillis() + ""));
        Files.createDirectories(tempBasePath);

        ArchiveParam archiveParam = ArchiveParam.builder()
                .type("zip")
                .encoding(sysProperties.getStore().getArchiveEncoding())
                .build();

        try(ArchiveExtractor extractor = archiveManager.getExtractor(archiveParam,resource)) {


            // 先解压文件到本地，并在网盘中先创建好文件夹
            try(
                    final ArchiveInputStream ignored = extractor.walk(((file, stream) -> {
                        Path localTemp = Paths.get(tempBasePath + "/" + file.getPath());
                        if (file.isDirectory()) {
                            log.debug("创建文件夹：{}", localTemp);
                            Files.createDirectories(localTemp);
                            fileSystem.mkdirs(uid, dest + "/" + file.getPath());
                        } else {
                            log.debug("解压文件：{}", localTemp);
                            Files.copy(stream, localTemp);
                        }
                        return ArchiveExtractorVisitor.Result.CONTINUE;
                    }))
            ) {
                Files.walkFileTree(tempBasePath, new SimpleFileVisitor<>() {
                    final int tempLen = tempBasePath.toString().length();

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String diskPath = dest + "/" + file.getParent().toString().substring(tempLen);
                        fileSystem.moveToSaveFile(uid, file, diskPath, FileInfo.getLocal(file.toString()));
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (ZipException | ArchiveException e) {
            JsonException exception = new JsonException(ArchiveError.ARCHIVE_FORMAT_UNSUPPORTED);
            exception.initCause(e);
            exception.setStackTrace(e.getStackTrace());
            e.printStackTrace();
            throw exception;
        } catch (IOException e) {
            e.printStackTrace();
            throw new JsonException(500, "存储出错或可能存在冲突的文件与文件夹名");
        } catch (Exception e) {
            e.printStackTrace();
            throw new JsonException(e.getMessage());
        }finally {
            // 清理临时解压目录
            // 创建压缩读取器时可能会抛出异常导致临时目录并未创建
            if (Files.exists(tempBasePath)) {
                log.debug("[解压文件]清理临时解压目录：{}", tempBasePath);
                FileUtils.delete(tempBasePath);
            }
            log.debug("[解压文件]文件 {} 解压完成", name);
        }
    }
}
