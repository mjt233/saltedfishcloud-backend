package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.projection.FileInfoSearchResult;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.model.param.FileTimeAttribute;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PRIVATE;
import static com.xiaotao.saltedfishcloud.model.FileSystemStatus.AREA_PUBLIC;

/**
 * 默认文件系统元数据操作实现，统一封装基于 FileRecordService 的文件记录与索引仓库访问。
 */
@Component
public class DefaultFileSystemMetadataOperator implements FileSystemMetadataOperator {
    /**
     * 文件信息仓库。
     */
    @Autowired
    private FileInfoRepo fileInfoRepo;

    /**
     * 文件记录服务。
     */
    @Autowired
    private FileRecordService fileRecordService;

    /**
     * 按完整路径获取文件记录。
     *
     * @param uid 用户ID
     * @param path 完整路径
     * @return 文件记录
     */
    public Optional<FileInfo> getByPath(long uid, String path) {
        return fileRecordService.getByPath(uid, path);
    }

    /**
     * 根据文件MD5获取文件记录。
     *
     * @param md5 文件MD5
     * @param limit 返回数量上限
     * @return 文件记录列表
     */
    public List<FileInfo> getFileInfoByMd5(String md5, int limit) {
        return fileRecordService.getFileInfoByMd5(md5, limit);
    }

    /**
     * 根据节点ID获取路径。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @return 对应路径
     */
    public Optional<String> getPathByNodeId(long uid, String nodeId) {
        return fileRecordService.getPathByNodeId(uid, nodeId);
    }

    /**
     * 保存文件记录。
     *
     * @param fileInfo 文件信息
     * @param path 所在目录
     */
    public void saveRecord(FileInfo fileInfo, String path) {
        fileRecordService.saveRecord(fileInfo, path);
    }

    /**
     * 保存文件记录实体。
     *
     * @param fileInfo 文件信息
     * @return 保存后的文件信息
     */
    public FileInfo save(FileInfo fileInfo) {
        return fileRecordService.save(fileInfo);
    }

    /**
     * 判断指定路径是否存在。
     *
     * @param uid 用户ID
     * @param path 完整路径
     * @return 存在返回true，否则返回false
     */
    public boolean exist(long uid, String path) {
        return fileRecordService.exist(uid,
                com.xiaotao.saltedfishcloud.utils.PathUtils.getParentPath(path),
                com.xiaotao.saltedfishcloud.utils.PathUtils.getLastNode(path));
    }

    /**
     * 获取指定目录下的文件记录。
     *
     * @param uid 用户ID
     * @param dirPath 目录路径
     * @param name 文件名
     * @return 文件记录
     */
    public FileInfo getFileInfo(long uid, String dirPath, String name) {
        return fileRecordService.getFileInfo(uid, dirPath, name);
    }

    /**
     * 按节点ID获取指定文件记录。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @param name 文件名
     * @return 文件记录
     */
    public FileInfo getFileInfoByNode(long uid, String nodeId, String name) {
        return fileRecordService.getFileInfoByNode(uid, nodeId, name);
    }

    /**
     * 根据路径获取节点ID。
     *
     * @param uid 用户ID
     * @param path 路径
     * @return 节点ID
     */
    public Optional<String> getNodeIdByPath(long uid, String path) {
        return fileRecordService.getNodeIdByPath(uid, path);
    }

    /**
     * 根据节点ID查询文件列表。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @return 文件列表
     */
    public List<FileInfo> findByUidAndNodeId(long uid, String nodeId) {
        return fileRecordService.findByUidAndNodeId(uid, nodeId);
    }

    /**
     * 根据节点ID和文件名列表查询文件列表。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @param nameList 文件名列表
     * @return 文件列表
     */
    public List<FileInfo> findByUidAndNodeId(long uid, String nodeId, @Nullable Collection<String> nameList) {
        return fileRecordService.findByUidAndNodeId(uid, nodeId, nameList);
    }

    /**
     * 复制文件记录。
     *
     * @param param 复制参数
     * @param callback 复制回调
     */
    public void copy(SimpleFileTransferParam param, @Nullable FileTransferCallback callback) {
        fileRecordService.copy(param, callback);
    }

    /**
     * 移动文件记录。
     *
     * @param uid 用户ID
     * @param source 源目录
     * @param target 目标目录
     * @param name 文件名
     * @param overwrite 是否覆盖
     * @throws NoSuchFileException 路径不存在时抛出
     */
    public void move(long uid, String source, String target, String name, boolean overwrite) throws NoSuchFileException {
        fileRecordService.move(uid, source, target, name, overwrite);
    }

    /**
     * 在同目录下批量保存文件记录。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param fileInfos 文件信息列表
     */
    public void batchSaveFileInSameDirectory(long uid, String path, List<FileInfo> fileInfos) {
        fileRecordService.batchSaveFileInSameDirectory(uid, path, fileInfos);
    }

    /**
     * 创建目录记录，若祖先目录不存在则一并创建。
     *
     * @param uid 用户ID
     * @param path 完整目录路径
     * @param isMount 是否为挂载目录
     * @return 创建后的节点ID
     */
    public String mkdirs(long uid, String path, boolean isMount) {
        return fileRecordService.mkdirs(uid, path, isMount);
    }

    /**
     * 获取目录节点ID，不存在时自动创建。
     *
     * @param uid 用户ID
     * @param path 完整目录路径
     * @param isMount 是否为挂载目录
     * @return 节点ID
     */
    public String getAndMkdirs(long uid, String path, boolean isMount) {
        return fileRecordService.getAndMkdirs(uid, path, isMount);
    }

    /**
     * 删除文件记录。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param names 文件名列表
     * @return 被删除的文件记录
     * @throws NoSuchFileException 路径不存在时抛出
     */
    public List<FileInfo> deleteRecords(long uid, String path, Collection<String> names) throws NoSuchFileException {
        return fileRecordService.deleteRecords(uid, path, names);
    }

    /**
     * 重命名文件记录。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param name 原文件名
     * @param newName 新文件名
     * @throws NoSuchFileException 路径不存在时抛出
     */
    public void rename(long uid, String path, String name, String newName) throws NoSuchFileException {
        fileRecordService.rename(uid, path, name, newName);
    }

    /**
     * 更新文件记录中的时间属性。
     *
     * @param uid 用户ID
     * @param path 父目录路径
     * @param names 文件名列表
     * @param attribute 时间属性
     */
    public void updateTime(long uid, String path, List<String> names, FileTimeAttribute attribute) {
        FileInfo dirInfo = getByPath(uid, path).orElse(null);
        if (dirInfo == null || dirInfo.isFile()) {
            return;
        }
        List<FileInfo> fileInfoList = findByUidAndNodeId(uid, dirInfo.getNode(), names);
        fileInfoList.forEach(fileInfo -> {
            if (attribute.apply(fileInfo)) {
                save(fileInfo);
            }
        });
    }

    /**
     * 按路径列出用户目录下的文件列表。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @return 文件列表数组
     */
    public List<FileInfo>[] getUserFileList(long uid, String path) {
        return getNodeIdByPath(uid, path)
                .map(nodeId -> getUserFileListByNodeId(uid, nodeId))
                .orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND));
    }

    /**
     * 按路径和文件名列表列出文件。
     *
     * @param uid 用户ID
     * @param path 目录路径
     * @param nameList 文件名列表
     * @return 文件信息列表
     */
    public List<FileInfo> getUserFileList(long uid, String path, @Nullable Collection<String> nameList) {
        return getNodeIdByPath(uid, path)
                .map(nodeId -> findByUidAndNodeId(uid, nodeId, nameList))
                .orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND, path));
    }

    /**
     * 按节点ID列出文件列表。
     *
     * @param uid 用户ID
     * @param nodeId 节点ID
     * @return 文件列表数组
     */
    @SuppressWarnings("unchecked")
    public List<FileInfo>[] getUserFileListByNodeId(long uid, String nodeId) {
        List<FileInfo> fileList = findByUidAndNodeId(uid, nodeId);
        List<FileInfo> dirs = new LinkedList<>();
        List<FileInfo> files = new LinkedList<>();
        fileList.forEach(file -> {
            if (file.isFile()) {
                file.setType(FileInfo.TYPE_FILE);
                files.add(file);
            } else {
                file.setType(FileInfo.TYPE_DIR);
                dirs.add(file);
            }
        });
        return new List[]{dirs, files};
    }

    /**
     * 执行文件搜索。
     *
     * @param uid 用户ID
     * @param key 搜索关键字
     * @param page 页码
     * @param size 页大小
     * @return 搜索结果
     */
    public CommonPageInfo<FileInfo> search(long uid, String key, Integer page, Integer size) {
        String searchKey = key.replace("%", "").replaceAll("\\s+", "%");
        int pageIndex = Math.max(Optional.ofNullable(page).orElse(0), 0);
        int pageSize = Math.max(Optional.ofNullable(size).orElse(10), 1);
        Page<FileInfoSearchResult> searchResult = fileInfoRepo.search(uid, searchKey, PageRequest.of(pageIndex, pageSize));
        CommonPageInfo<FileInfo> pageInfo = new CommonPageInfo<>();
        pageInfo.setTotalPage(searchResult.getTotalPages());
        pageInfo.setTotalCount(searchResult.getTotalElements());
        pageInfo.setContent(searchResult.getContent().stream().map(item -> {
            FileInfo fileInfo = item.getFileInfo();
            fileInfo.setParent(item.getParent());
            return fileInfo;
        }).toList());
        return pageInfo;
    }

    /**
     * 合并主存储状态与文件记录统计状态。
     *
     * @param storeStatuses 主存储状态列表
     * @return 合并后的状态列表
     */
    public List<FileSystemStatus> buildStatus(@Nullable List<FileSystemStatus> storeStatuses) {
        Map<String, FileSystemStatus> areaMap;
        if (storeStatuses != null) {
            areaMap = storeStatuses.stream().collect(Collectors.toMap(FileSystemStatus::getArea, item -> item));
        } else {
            areaMap = new HashMap<>();
        }
        areaMap.putIfAbsent(AREA_PRIVATE, FileSystemStatus.builder().area(AREA_PRIVATE).build());
        areaMap.putIfAbsent(AREA_PUBLIC, FileSystemStatus.builder().area(AREA_PUBLIC).build());

        FileSystemStatus publicStatus = areaMap.get(AREA_PUBLIC);
        LazyFileSystemStatus lazyPublicStatus = new LazyFileSystemStatus(fileInfoRepo);
        org.springframework.beans.BeanUtils.copyProperties(publicStatus, lazyPublicStatus);

        FileSystemStatus privateStatus = areaMap.get(AREA_PRIVATE);
        LazyFileSystemStatus lazyPrivateStatus = new LazyFileSystemStatus(fileInfoRepo);
        org.springframework.beans.BeanUtils.copyProperties(privateStatus, lazyPrivateStatus);
        return Arrays.asList(lazyPublicStatus, lazyPrivateStatus);
    }

    /**
     * 惰性文件系统状态，只有读取统计信息时才访问数据库。
     */
    @RequiredArgsConstructor
    private static class LazyFileSystemStatus extends FileSystemStatus {
        @JsonIgnore
        private transient final FileInfoRepo fileInfoRepo;

        /**
         * 判断当前是否为私人区域。
         *
         * @return 私人区域返回true，否则返回false
         */
        private boolean isPrivate() {
            return AREA_PRIVATE.equals(this.getArea());
        }

        @Override
        public Long getFileCount() {
            if (super.getFileCount() == null) {
                this.setFileCount(isPrivate() ? fileInfoRepo.getUserFileCount() : fileInfoRepo.getPublicFileCount());
            }
            return super.getFileCount();
        }

        @Override
        public Long getDirCount() {
            if (super.getDirCount() == null) {
                this.setDirCount(isPrivate() ? fileInfoRepo.getUserDirCount() : fileInfoRepo.getPublicDirCount());
            }
            return super.getDirCount();
        }

        @Override
        public Long getSysUsed() {
            if (super.getSysUsed() == null) {
                this.setSysUsed(isPrivate() ? fileInfoRepo.getUserTotalSize() : fileInfoRepo.getPublicTotalSize());
            }
            return super.getSysUsed();
        }
    }
}
