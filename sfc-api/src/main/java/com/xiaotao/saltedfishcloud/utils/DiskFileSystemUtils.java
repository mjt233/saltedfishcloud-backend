package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.DiskFileAttributes;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@UtilityClass
public class DiskFileSystemUtils {

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
