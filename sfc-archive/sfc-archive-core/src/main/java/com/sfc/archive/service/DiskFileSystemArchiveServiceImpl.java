package com.sfc.archive.service;

import com.sfc.archive.*;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.*;
import com.sfc.archive.utils.EngineResourceUtils;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.enums.ArchiveError;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipException;

/**
 * 默认的网盘文件系统压缩、解压缩服务实现类
 */
@Slf4j
@Service
public class DiskFileSystemArchiveServiceImpl implements DiskFileSystemArchiveService {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private SysProperties sysProperties;

    @Autowired
    private ArchiveManager archiveManager;

    @Autowired
    private ArchiveEngineManager archiveEngineManager;

    @Autowired
    private AsyncTaskManager asyncTaskManager;


    public void doCompressZipAndWriteOut(long uid, String path, Collection<String> names, CompressionLevel compressionLevel, OutputStream outputStream) throws IOException {
        ArchiveEngineProvider engine = archiveEngineManager.getDecompressProviderByFilename("tmp.zip")
                .stream()
                .findAny()
                .orElseThrow(() -> new UnsupportedOperationException("找不到支持zip的ArchiveEngine"));

        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        try(ArchiveEngineCompressor compressor = engine.createCompressor(outputStream, ArchiveProperty.builder()
                .compressionLevel(compressionLevel)
                .encoding(sysProperties.getStore().getArchiveEncoding())
                .build())) {
            for (FileInfo fileInfo : fileSystem.getUserFileList(uid, path, names)) {
                if (fileInfo.isFile()) {
                    compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(fileInfo, "/", fileSystem.getResource(uid, path, fileInfo.getName())));
                } else {
                    // 创建文件夹本身
                    compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(fileInfo, "/", null));
                    DiskFileSystemUtils.walk(fileSystem, uid, StringUtils.appendPath(path, fileInfo.getName()), (curPath, subFiles) -> {
                        String basePath = StringUtils.removePrefix(path, curPath);
                        if (basePath.isEmpty()) {
                            basePath = "/";
                        }

                        // 向文件夹内压缩文件
                        for (FileInfo subFile : subFiles) {
                            if (subFile.isFile()) {
                                compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(subFile, basePath, fileSystem.getResource(uid, curPath, subFile.getName())));
                            } else {
                                compressor.addArchiveResource(EngineResourceUtils.toArchiveResource(subFile, basePath, null));
                            }
                        }
                    });
                }
            }
        }
    }


    @Override
    public void compressAndWriteOut(long uid, String path, Collection<String> names, OutputStream outputStream) throws IOException {
        doCompressZipAndWriteOut(uid, path, names, CompressionLevel.STORE, outputStream);
    }


    @Override
    public void compress(long uid, String path, Collection<String> names, String dest) throws IOException {
        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        DiskFileSystemArchiveHelper.testIsFileWritable(fileSystem, uid, dest);
        Path temp = Paths.get(PathUtils.getTempDirectory() + "/temp_zip" + System.currentTimeMillis());
        try(OutputStream output = Files.newOutputStream(temp)) {
            doCompressZipAndWriteOut(uid, path, names, CompressionLevel.NORMAL, output);

            final FileInfo fileInfo = FileInfo.getLocal(temp.toString());

            PathBuilder pb = new PathBuilder();
            pb.setForcePrefix(true);
            pb.append(dest);

            fileInfo.setName(pb.range(1, -1).replace("/", ""));

            fileSystem.moveToSaveFile(uid, temp, pb.range(-1), fileInfo);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public long asyncCompress(DiskFileSystemCompressParam param) throws IOException {
        AsyncTaskRecord record = new AsyncTaskRecord();
        record.setName(AsyncTaskType.ARCHIVE_COMPRESS + "-" + param.getArchiveParam().getType());
        record.setTaskType(AsyncTaskType.ARCHIVE_COMPRESS);
        record.setCpuOverhead(10);
        record.setParams(MapperHolder.toJson(param));
        User curUser = SecureUtils.getSpringSecurityUser();
        record.setUid(Optional.ofNullable(curUser).map(e -> e.getId()).orElse(param.getSourceUid()));

        asyncTaskManager.submitAsyncTask(record);

        if (Boolean.TRUE.equals(param.getWaitExit())) {
            try {
                return Optional.ofNullable(asyncTaskManager.waitTaskExit(record.getId(), 1, TimeUnit.HOURS))
                        .orElse(record)
                        .getId();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        return record.getId();
    }

    /**
     * todo 使用任务队列控制同时进行解压缩的数量
     * todo 边解压边计算MD5
     * todo 实现实时计算MD5的IO流
     * todo 封装代码
     */
    @Override
    public void extractArchive(long uid, String path, String name, String dest) throws IOException {
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
            extractor.extractAll(tempBasePath);
            Files.walkFileTree(tempBasePath, new SimpleFileVisitor<>() {
                final int tempLen = tempBasePath.toString().length();

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String diskPath = StringUtils.appendPath(dest, (dir.toString().substring(tempLen)).replaceAll("\\\\+", "/"));
                    if(!fileSystem.exist(uid, diskPath)) {
                        fileSystem.mkdirs(uid, diskPath);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String diskPath = StringUtils.appendPath(dest, (file.getParent().toString().substring(tempLen)).replaceAll("\\\\+", "/"));
                    fileSystem.moveToSaveFile(uid, file, diskPath, FileInfo.getLocal(file.toString()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (ZipException | ArchiveException e) {
            JsonException exception = new JsonException(ArchiveError.ARCHIVE_FORMAT_UNSUPPORTED);
            exception.initCause(e);
            exception.setStackTrace(e.getStackTrace());
            e.printStackTrace();
            throw exception;
        } catch (IOException e) {
            e.printStackTrace();
            throw new JsonException(500, "解压缩出错: " + Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage()));
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
