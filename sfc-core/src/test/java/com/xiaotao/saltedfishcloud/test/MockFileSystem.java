package com.xiaotao.saltedfishcloud.test;

import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MockFileSystem {

    private final Map<String, List<FileInfo>> virtualDirParentMap = new HashMap<>();
    private final Map<String, FileInfo> virtualDirMap = new HashMap<>();
    private final Map<String, String> nameMd5Map = new HashMap<>();

    /**
     * 通过文件名获取文件md5。注意：当存在多个同名文件时，该方法的返回值是不确定的
     * @param name 已在Mock文件系统中注册的文件名
     */
    public String getMd5ByName(String name) {
        return nameMd5Map.get(name);
    }

    /**
     * 根据FileInfo的md5获取目录类型的文件对象信息。<br>
     * 目录类型的文件信息的md5会作为目录节点id。
     */
    public FileInfo getDirFileInfoByMd5(String md5) {
        return virtualDirMap.get(md5);
    }

    /**
     * 列出指定目录下的所有文件
     * @param parentNodeId  目录节点id
     */
    public List<FileInfo> listFiles(String parentNodeId) {
        return virtualDirParentMap.get(parentNodeId);
    }

    /**
     * Mock 文件系统快捷操作类，用于通过简洁的流式API调用快速创建Mock层级目录
     */
    public class MockFileSystemHandler {
        /**
         * 上级目录节点，后续的创建子文件和子目录操作均以该文件为上级
         */
        @Getter
        private final FileInfo parent;

        public MockFileSystemHandler(FileInfo parent) {
            this.parent = parent;
        }

        /**
         * 在下级创建一个文件
         * @param name  文件名
         */
        public void addSubFile(String name) {
            registerMockFile(createFile(name, parent.getMd5()));
        }

        /**
         * 在下级创建一个目录
         * @param name  目录名
         * @return  下级目录的 MockFileSystemHandler
         */
        public MockFileSystemHandler addSubDir(String name) {
            return registerMockFile(createDir(name, parent.getMd5()));
        }

        /**
         * 一般用于以当前文件为上级，添加多个下级文件或目录
         */
        public void andThen(Consumer<MockFileSystemHandler> consumer) {
            consumer.accept(this);
        }
    }

    /**
     * 创建一个类型为目录的文件对象信息
     * @param name      文件名
     * @param parent    上级目录节点id（FileInfo.getMd5()）
     */
    public static FileInfo createDir(String name, String parent) {
        FileInfo f = new FileInfo().setName(name).setMd5(SecureUtils.getUUID()).setNode(parent);
        f.setSize(-1L);
        f.setUid(0L);
        f.setId(IdUtil.getId());
        return f;
    }

    /**
     * 创建一个文件对象信息
     * @param name   文件名
     * @param parent    上级目录节点id（FileInfo.getMd5()）
     */
    public static FileInfo createFile(String name, String parent) {
        return createDir(name, parent).setSize(114514L);
    }


    /**
     * 向模拟文件系统数据集中添加一个文件或目录
     *
     * @param fileInfo 待添加的文件或目录
     */
    public MockFileSystemHandler registerMockFile(FileInfo fileInfo) {
        virtualDirParentMap
                .computeIfAbsent(fileInfo.getNode(), k -> new ArrayList<>())
                .add(fileInfo);
        if (fileInfo.isDir()) {
            nameMd5Map.put(fileInfo.getName(), fileInfo.getMd5());
            virtualDirMap.put(fileInfo.getMd5(), fileInfo);
        }
        return new MockFileSystemHandler(fileInfo);
    }

}
