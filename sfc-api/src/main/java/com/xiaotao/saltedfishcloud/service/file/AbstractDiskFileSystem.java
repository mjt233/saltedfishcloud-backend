package com.xiaotao.saltedfishcloud.service.file;

import com.sfc.archive.ArchiveManager;
import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.comporessor.ArchiveResourceEntry;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.extractor.ArchiveExtractorVisitor;
import com.sfc.archive.model.ArchiveParam;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.sfc.constant.error.FileSystemError;
import com.sfc.enums.ArchiveError;
import com.sfc.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipException;

/**
 * 抽象文件系统，初步实现了文件解压缩操作
 * todo 文件解压缩需要从文件系统中解耦，由继承改为组合，并通过另外的接口单独维护解压缩功能。
 */
@Slf4j
public abstract class AbstractDiskFileSystem implements DiskFileSystem {

    protected abstract RedissonClient getRedissonClient();

    protected abstract SysProperties getSysProperties();

    protected abstract ArchiveManager getArchiveManager();

    @Override
    public void compressAndWriteOut(int uid, String path, Collection<String> names, ArchiveType type, OutputStream outputStream) throws IOException {
        ArchiveParam archiveParam = ArchiveParam.builder()
                .encoding(getSysProperties().getStore().getArchiveEncoding())
                .type("zip")
                .build();
        try(ArchiveCompressor compressor = getArchiveManager().getCompressor(archiveParam, outputStream)) {
            compress(uid, path, path, names, compressor);
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
            Resource resource = getResource(uid, path, name);
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
        List<FileInfo>[] list = getUserFileList(uid, path);
        String curPath = StringUtils.removePrefix(root, path).replaceAll("//+", "/").replaceAll("^/+", "");
        compressor.addFile(new ArchiveResourceEntry(
                curPath + "/",
                0,
                null
        ));
        for (FileInfo file : list[1]) {
            compressor.addFile(new ArchiveResourceEntry(
                    curPath + "/" + file.getName(), file.getSize(), getResource(uid, path, file.getName()))
            );
        }

        for (FileInfo file : list[0]) {
            compressor.addFile(new ArchiveResourceEntry(curPath + "/" + file.getName() + "/", 0, null));
            compressDir(uid, root, path + "/" + file.getName(), compressor, depth + 1);
        }
    }

    /**
     * 获取写文件时用到分布式锁key
     * @param uid   用户id
     * @param dest  文件所在路径
     */
    public static String getStoreLockKey(int uid, String dest) {
        return uid + ":" + dest;
    }

    @Override
    public void compress(int uid, String path, Collection<String> names, String dest, ArchiveType type) throws IOException {
        boolean exist = exist(uid, dest);
        if (exist && getResource(uid, dest, "") == null) {
            throw new JsonException(FileSystemError.RESOURCE_TYPE_NOT_MATCH);
        }
        Path temp = Paths.get(PathUtils.getTempDirectory() + "/temp_zip" + System.currentTimeMillis());
        RLock lock = getRedissonClient().getLock(getStoreLockKey(uid, dest));
        lock.lock();
        try(OutputStream output = Files.newOutputStream(temp)) {
            PathBuilder pb = new PathBuilder();
            pb.setForcePrefix(true);
            pb.append(dest);
            compressAndWriteOut(uid, path, names, ArchiveType.ZIP, output);
            final FileInfo fileInfo = FileInfo.getLocal(temp.toString());
            fileInfo.setName(pb.range(1, -1).replace("/", ""));

            if (exist) {
                deleteFile(uid, PathUtils.getParentPath(dest), Collections.singletonList(PathUtils.getLastNode(dest)));
            }
            moveToSaveFile(uid, temp, pb.range(-1), fileInfo);
        } finally {
            Files.deleteIfExists(temp);
            lock.unlock();
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
        if (!name.endsWith(".zip")) {
            throw new JsonException(ArchiveError.ARCHIVE_FORMAT_UNSUPPORTED);
        }
        Resource resource = getResource(uid, path, name);
        if (resource == null) throw new NoSuchFileException(path + "/" + name);


        // 创建临时目录用于存放临时解压的文件
        Path tempBasePath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), System.currentTimeMillis() + ""));
        Path zipFilePath;
        boolean isDownloadZip = false;
        File zipFile;
        try {
            zipFile = resource.getFile();
            zipFilePath = Paths.get(zipFile.getAbsolutePath());
        } catch (FileNotFoundException ignore) {
            // getFile异常时，需要从Resource的InputStream保存文件到本地
            isDownloadZip = true;
            zipFilePath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), StringUtils.getRandomString(6) + ".zip"));
            try(
                    final InputStream is = resource.getInputStream();
                    final OutputStream os = Files.newOutputStream(zipFilePath)
            ) {
                log.debug("[解压文件]非本地文件系统存储服务，需要复制文件到本地文件系统：{} ", resource.getFilename());
                log.debug("[解压文件]临时文件：{}", zipFilePath);
                StreamUtils.copy(is, os);
            } catch (IOException e) {
                log.debug("[解压文件]临时文件保存出错");
                Files.deleteIfExists(zipFilePath);
                throw e;
            }
            zipFile = zipFilePath.toFile();
        }

        try(

                ArchiveExtractor fileSystem = getArchiveManager()
                        .getExtractor(
                            ArchiveParam.builder().type("zip").encoding(getSysProperties().getStore().getArchiveEncoding()).build(),
                            new PathResource(Paths.get(zipFile.getPath()))
                        )
        ) {

            Files.createDirectories(tempBasePath);

            // 先解压文件到本地，并在网盘中先创建好文件夹
            try(
                    final ArchiveInputStream ignored = fileSystem.walk(((file, stream) -> {
                        Path localTemp = Paths.get(tempBasePath + "/" + file.getPath());
                        if (file.isDirectory()) {
                            log.debug("创建文件夹：{}", localTemp);
                            Files.createDirectories(localTemp);
                            mkdirs(uid, dest + "/" + file.getPath());
                        } else {
                            log.debug("解压文件：{}", localTemp);
                            Files.copy(stream, localTemp);
                        }
                        return ArchiveExtractorVisitor.Result.CONTINUE;
                    }))
            ) {
                Files.walkFileTree(tempBasePath, new SimpleFileVisitor<Path>() {
                    final int tempLen = tempBasePath.toString().length();
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String diskPath = dest + "/" + file.getParent().toString().substring(tempLen);
                        moveToSaveFile(uid, file, diskPath, FileInfo.getLocal(file.toString()));
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
            if (isDownloadZip) {
                log.debug("[解压文件]清理临时压缩包：{}", zipFilePath);
                Files.delete(zipFilePath);
            }
            log.debug("[解压文件]文件 {} 解压完成", name);
        }
    }
}
