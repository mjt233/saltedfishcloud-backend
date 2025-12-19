package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.DiskFileAttributes;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.xiaotao.saltedfishcloud.constant.ByteSize._1KiB;

@UtilityClass
public class DiskFileSystemUtils {

    public final static int FILE_BUFFER_SIZE = 64 * _1KiB;

    /**
     * 保存文件流，并实时计算文件的md5和实际保存的大小，反写到参数file中
     * @param file  通过streamSource获取文件输入流，同时用于接收文件大小和md5的文件信息
     * @param os    文件输出流
     */
    public static StreamCopyResult saveFile(FileInfo file, OutputStream os) throws IOException {
        if (file.getStreamSource() == null) {
            throw new IllegalArgumentException("fileInfo缺少streamSource");
        }
        try(InputStream is = file.getStreamSource().getInputStream()) {
            return saveFileStream(file, is, os);
        }
    }

    /**
     * 保存文件流，并实时计算文件的md5和实际保存的大小，反写到参数file中
     * @param file  接收文件大小和md5的文件信息
     * @param is    文件输入流
     * @param os    文件输出流
     */
    public static StreamCopyResult saveFileStream(FileInfo file, InputStream is, OutputStream os) throws IOException {
        return StreamUtils.copyStreamAndComputeMd5(is, os, file.getMd5()).applyTo(file);
    }

    /**
     * 处理统一资源请求文件流的保存逻辑
     * @param is    文件输入流。方法调用完后不会关闭。
     * @param resourceRequest   统一资源请求参数
     * @param os    保存文件的输出流。方法调用完后不会关闭。
     */
    public static StreamCopyResult saveResourceFileStream(InputStream is, ResourceRequest resourceRequest, OutputStream os) throws IOException {
        return StreamUtils.copyStreamAndComputeMd5(is, os, resourceRequest.getMd5())
                .applyTo(resourceRequest);
    }

    /**
     * 遍历文件系统
     * @param fileSystem    文件系统
     * @param uid           用户id
     * @param path          起始路径
     * @param consumer      消费函数，第一个参数为当前路径，第二个参数为当前路径下的文件项目
     */
    public static void walk(DiskFileSystem fileSystem, Long uid, String path, BiConsumer<String, List<FileInfo>> consumer) throws IOException {
        Deque<String> pathQueue = new ArrayDeque<>();
        pathQueue.add(path);
        do {
            String curPath = pathQueue.pop();
            List<FileInfo>[] userFileList = fileSystem.getUserFileList(uid, curPath);
            if (userFileList == null || userFileList.length == 0) {
                continue;
            }
            List<FileInfo> fileList = new ArrayList<>(userFileList[0].size() + userFileList[1].size());
            fileList.addAll(userFileList[0]);
            fileList.addAll(userFileList[1]);
            if (!userFileList[0].isEmpty()) {
                pathQueue.addAll(userFileList[0].stream().map(e -> StringUtils.appendPath(curPath, e.getName())).collect(Collectors.toList()));
            }
            consumer.accept(curPath, fileList);
        } while (!pathQueue.isEmpty());
    }

    /**
     * 遍历文件系统
     * @param fileSystem    文件系统
     * @param uid           用户id
     * @param path          起始路径
     * @param visitor       遍历器
     */
    public static void walk(DiskFileSystem fileSystem, Long uid, String path, FileVisitor<FileInfo> visitor) throws IOException {
        Deque<String> pathQueue = new ArrayDeque<>();
        pathQueue.add(path);
        do {
            int newSubDirCount = 0;
            String curPath = pathQueue.pop();
            List<FileInfo>[] userFileList = fileSystem.getUserFileList(uid, curPath);
            if (userFileList == null || userFileList.length == 0) {
                continue;
            }

            boolean isSkipSibling = false;
            for (List<FileInfo> fileList : userFileList) {
                if (isSkipSibling) {
                    break;
                }

                for (FileInfo file : fileList) {
                    String filePath = StringUtils.appendPath(curPath, file.getName());
                    DiskFileAttributes attr = DiskFileAttributes.from(file);
                    file.setPath(filePath);

                    if (file.isDir()) {
                        FileVisitResult fileVisitResult = visitor.preVisitDirectory(file, attr);
                        switch (fileVisitResult){
                            case CONTINUE:
                                pathQueue.add(filePath);
                                newSubDirCount++;
                                break;
                            case TERMINATE:
                                return;
                            case SKIP_SIBLINGS:
                                isSkipSibling = true;
                                while (newSubDirCount > 0) {
                                    pathQueue.removeLast();
                                    newSubDirCount--;
                                }
                                break;
                        }
                    }
                    if (isSkipSibling) {
                        break;
                    }
                    FileVisitResult fileVisitResult;
                    try {
                        fileVisitResult = visitor.visitFile(file, attr);
                        if (file.isDir()) {
                            visitor.postVisitDirectory(file, null);
                        }
                    } catch (IOException e) {
                        fileVisitResult = visitor.visitFileFailed(file, e);
                    }
                    if (fileVisitResult == FileVisitResult.TERMINATE) { return; }
                    if (fileVisitResult == FileVisitResult.SKIP_SIBLINGS) { break; }
                }


            }
        } while (!pathQueue.isEmpty());
    }
}
