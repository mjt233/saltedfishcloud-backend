package com.sfc.archive;

import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.comporessor.ArchiveResourceEntry;
import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.sfc.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


public class DiskFileSystemArchiveHelper {
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
    private static void compress(DiskFileSystemCompressParam param, ArchiveCompressor compressor, DiskFileSystem fileSystem) throws IOException {
        String curDir = param.getSourcePath().replaceAll("//+", "").replaceAll("^/+", "");
        int uid = param.getSourceUid().intValue();
        String root = param.getSourcePath().replaceAll("//+", "");

        for (String name : param.getSourceNames()) {

            Resource resource = fileSystem.getResource(uid, param.getSourcePath(), name);
            if (resource == null) {
                compressDir(uid, root, StringUtils.appendPath(root, name), compressor, fileSystem,1);
            } else {
                compressor.addFile(new ArchiveResourceEntry(
                        StringUtils.removePrefix(root, curDir.length() == 0 ? name : StringUtils.appendPath(curDir, name)),
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
    private static void compressDir(int uid, String root, String path, ArchiveCompressor compressor, DiskFileSystem fileSystem, int depth) throws IOException {
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
            compressDir(uid, root, path + "/" + file.getName(), compressor, fileSystem,depth + 1);
        }
    }
}
