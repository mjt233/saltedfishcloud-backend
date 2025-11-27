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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.xiaotao.saltedfishcloud.constant.ByteSize._1KiB;

@UtilityClass
public class DiskFileSystemUtils {

    public final static int FILE_BUFFER_SIZE = 64 * _1KiB;

    /**
     * 处理统一资源请求文件流的保存逻辑
     * @param is    文件输入流
     * @param resourceRequest   统一资源请求参数
     * @param os    保存文件的输出流
     */
    public static void saveResourceFileStream(InputStream is, ResourceRequest resourceRequest, OutputStream os) throws IOException {
        String inputMd5 = Optional.ofNullable(resourceRequest.getParams()).map(m -> m.get("md5")).orElse(null);
        try {
            byte[] buffer = new byte[FILE_BUFFER_SIZE];
            int len;
            MessageDigest md5 = MessageDigest.getInstance("md5");

            // 读取上传的文件数据后，同时计算md5和写入文件
            while ( (len = is.read(buffer, 0, buffer.length)) != -1 ) {
                md5.update(buffer, 0, len);
                os.write(buffer, 0, len);
            }
            String actualMd5Value = new String(encodeHex(md5.digest()));

            // 校验md5是否一致
            if (org.springframework.util.StringUtils.hasText(inputMd5) && !actualMd5Value.equals(inputMd5)) {
                throw new IllegalArgumentException("md5 is incorrect, actual md5 is " + actualMd5Value);
            }

            // 记录本次上传文件的md5
            Optional.ofNullable(resourceRequest.getParams())
                    .orElseGet(() -> {
                        resourceRequest.setParams(new HashMap<>());
                        return resourceRequest.getParams();
                    })
                    .put("md5", actualMd5Value);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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


    private static char[] encodeHex(byte[] bytes) {
        char[] HEX_CHARS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        char[] chars = new char[32];
        for (int i = 0; i < chars.length; i = i + 2) {
            byte b = bytes[i / 2];
            chars[i] = HEX_CHARS[(b >>> 0x4) & 0xf];
            chars[i + 1] = HEX_CHARS[b & 0xf];
        }
        return chars;
    }
}
