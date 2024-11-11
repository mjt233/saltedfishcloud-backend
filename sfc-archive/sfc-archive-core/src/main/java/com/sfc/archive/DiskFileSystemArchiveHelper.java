package com.sfc.archive;

import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.comporessor.ArchiveResourceEntry;
import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class DiskFileSystemArchiveHelper {
    private final static String LOG_TITLE = "Archive";

    /**
     * 从文件系统上读取文件进行压缩打包，并将压缩结果直接输出到指定的输出流中而不受参数影响
     * @param diskFileSystem    网盘文件系统
     * @param archiveManager    压缩服务管理器
     * @param param 压缩参数
     * @param outputStream 输出流
     */
    public static ArchiveCompressor compressAndWriteOut(DiskFileSystem diskFileSystem, ArchiveManager archiveManager, DiskFileSystemCompressParam param, OutputStream outputStream) throws IOException {
        ArchiveCompressor compressor = archiveManager.getCompressor(param.getArchiveParam(), outputStream);
        compress(param, compressor, diskFileSystem);
        return compressor;
    }

    /**
     * 测试能否在文件系统中写入目标文件，防止向一个同名目录路径中写入文件。若无法写入则抛出JsonException。
     * @param fileSystem    文件系统
     * @param uid           用户id
     * @param dest          待写入文件
     */
    public static void testIsFileWritable(DiskFileSystem fileSystem, long uid, String dest) throws IOException {
        boolean exist = fileSystem.exist((int)uid, dest);
        if (exist && fileSystem.getResource((int)uid, dest, "") == null) {
            throw new JsonException(FileSystemError.RESOURCE_TYPE_NOT_MATCH);
        }
    }


    /**
     * 压缩目录下指定的文件
     * @param param         压缩参数
     * @param fileSystem    网盘文件系统
     * @param compressor    压缩器
     */
    public static void compress(DiskFileSystemCompressParam param, ArchiveCompressor compressor, DiskFileSystem fileSystem) throws IOException {
        String curDir = param.getSourcePath().replaceAll("//+", "").replaceAll("^/+", "");
        long uid = param.getSourceUid();
        String root = param.getSourcePath().replaceAll("//+", "");
        Map<String, FileInfo> fileInfoMap = fileSystem.getUserFileList(uid, param.getSourcePath(), param.getSourceNames()).stream()
                .collect(Collectors.toMap(
                        FileInfo::getName,
                        Function.identity()
                ));
        for (String name : param.getSourceNames()) {
            if (Thread.interrupted()) {
                throw new IllegalStateException("压缩中断");
            }
            FileInfo fileInfo = fileInfoMap.get(name);
            if (fileInfo == null) {
                throw new IllegalArgumentException("在" + param.getSourcePath() + "下缺失文件" + name + "的信息");
            }

            Resource resource = fileSystem.getResource(uid, param.getSourcePath(), name);
            if (resource == null) {
                compressDir(fileInfo, uid, root, StringUtils.appendPath(root, name), compressor, fileSystem,1);
            } else {
                String archiveFilename;
                if ("/".equals(root)) {
                    archiveFilename = curDir.length() == 0 ? name : StringUtils.appendPath(curDir, name);
                } else {
                    archiveFilename = StringUtils.removePrefix(root, curDir.length() == 0 ? name : StringUtils.appendPath(curDir, name));
                }
                ArchiveResourceEntry entry = new ArchiveResourceEntry(
                        archiveFilename,
                        resource.contentLength(),
                        resource
                );
                entry.setMtime(fileInfo.getMtime());
                entry.setCtime(fileInfo.getCtime());
                compressor.addFile(entry);
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
    private static void compressDir(FileInfo fileInfo, long uid, String root, String path, ArchiveCompressor compressor, DiskFileSystem fileSystem, int depth) throws IOException {
        checkInterrupt();
        List<FileInfo>[] list = fileSystem.getUserFileList(uid, path);
        String curPath = StringUtils.removePrefix(root, path).replaceAll("//+", "/").replaceAll("^/+", "");
        ArchiveResourceEntry dirEntry = new ArchiveResourceEntry(
                curPath + "/",
                0,
                null
        );
        dirEntry.setMtime(fileInfo.getMtime());
        dirEntry.setCtime(fileInfo.getCtime());
        compressor.addFile(dirEntry);
        for (FileInfo file : list[1]) {
            checkInterrupt();
            ArchiveResourceEntry entry = new ArchiveResourceEntry(
                    curPath + "/" + file.getName(), file.getSize(), fileSystem.getResource(uid, path, file.getName()));
            entry.setMtime(file.getMtime());
            entry.setCtime(file.getCtime());
            compressor.addFile(entry);
        }

        for (FileInfo file : list[0]) {
            checkInterrupt();
            ArchiveResourceEntry entry = new ArchiveResourceEntry(curPath + "/" + file.getName() + "/", 0, null);
            entry.setMtime(file.getMtime());
            entry.setCtime(file.getCtime());
            compressor.addFile(entry);
            compressDir(file, uid, root, path + "/" + file.getName(), compressor, fileSystem,depth + 1);
        }
    }

    /**
     * 检查压缩线程是否中断
     */
    private static void checkInterrupt() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("执行中断");
        }
    }
}
