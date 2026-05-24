package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferCallback;
import com.xiaotao.saltedfishcloud.model.progress.FileTransferItem;
import com.xiaotao.saltedfishcloud.model.progress.event.EnterDirEvent;
import com.xiaotao.saltedfishcloud.model.progress.event.MountPointSkipEvent;
import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.node.FileTree;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.NoSuchFileException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 文件索引记录服务，管理数据库中的文件结构表
 */
@Service
@Slf4j
public class FileRecordServiceImpl implements FileRecordService {
    private final static String LOG_PREFIX = "[文件记录]";

    @Autowired
    private JdbcTemplate jdbcTemplate;

//    @Resource
//    private NodeCacheService cacheService;

    @Resource
    private FileInfoRepo fileInfoRepo;

    private static class PathIdPair {
        public String path;
        public String nid;

        public PathIdPair(String path, String nid) {
            this.path = path;
            this.nid = nid;
        }
    }

    @Override
    public FileInfo getFileInfo(long uid, String dirPath, String name) {
        String requestDir = name == null ? PathUtils.getParentPath(dirPath) : dirPath;
        String requestName = name == null ? PathUtils.getLastNode(dirPath) : name;
        return this.getNodeIdByPath(uid, requestDir)
                .map(nodeId -> fileInfoRepo.findFileInfo(uid, requestName, nodeId))
                .orElse(null);
    }

    @Override
    public FileInfo getFileInfoByNode(long uid, String nid, String name) {
        return fileInfoRepo.findFileInfo(uid, name, nid);
    }

    @Override
    public boolean exist(long uid, String path, String name) {
        String nid = this.getNodeIdByPath(uid, path).orElse(null);
        if (nid == null) {
            return false;
        }
        if ((uid + "").equals(nid) && (name == null || name.isEmpty())) {
            return true;
        }
        return fileInfoRepo.findFileInfo(uid, name, nid) != null;
    }

    @Override
    public List<FileInfo> getFileInfoByMd5(String md5, int limit) {
        Page<FileInfo> page = fileInfoRepo.findByMd5(md5, PageRequest.of(0, limit));
        return page.getContent();
    }

    @Override
    public Optional<FileInfo> getDirByMd5(Long uid, String md5) {
        return fileInfoRepo.findOne(JpaLambdaQueryWrapper
                .get(FileInfo.class)
                .eq(FileInfo::getMd5, md5)
                .eq(FileInfo::getUid, uid)
                .build()
        );
    }

    @Override
    public List<FileInfo> findByUidAndNodeId(Long uid, String nodeId, @Nullable Collection<String> nameList) {
        if (nameList == null || nameList.isEmpty()) {
            return findByUidAndNodeId(uid, nodeId);
        } else {
            return fileInfoRepo.findFileInfoByNames(uid, nameList, nodeId);
        }
    }

    @Override
    public void copy(SimpleFileTransferParam param, FileTransferCallback callback) {
        this.doCopy(param, callback, 0);
    }

    private void doCopy(SimpleFileTransferParam param, @Nullable FileTransferCallback callback, int depth) {
        if (depth >= 32) {
            throw new JsonException(FileSystemError.DIR_TOO_DEPTH, depth + "");
        }
        if (callback != null && callback.shouldInterrupt()) {
            return;
        }

        // 查询待复制的源文件
        String sourceNodeId = getNodeIdByPath(param.getSourceUid(), param.getSourcePath()).orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND, param.getSourcePath()));
        List<FileInfo> sourceFileItemList = findByUidAndNodeId(param.getSourceUid(), sourceNodeId, param.getFiles())
                .stream()
                .map(f -> {
                    FileInfo fileInfo = new FileInfo();
                    BeanUtils.copyProperties(f, fileInfo);

                    // 生成一个新ID
                    fileInfo.setId(IdUtil.getId());

                    // 目录类型的文件要设置个新的目录节点id标识
                    if (fileInfo.isDir()) {
                        fileInfo.setMd5(SecureUtils.getUUID());
                    }
                    return fileInfo;
                })
                .toList();

        // 找出挂载点，不复制挂载点下面的文件，并消除挂载信息
        Set<Long> mountPointDirIdSet = sourceFileItemList
                .stream()
                .filter(f -> f.getMountId() != null)
                .peek(f -> {
                    f.setMountId(null);
                    if(callback != null) {
                        String from = StringUtils.appendPath(param.getSourcePath(), f.getName());
                        String to = StringUtils.appendPath(param.getTargetPath(), f.getName());
                        callback.onAdditionalEvent(MountPointSkipEvent.of(f.getName(), from, to,
                                FileTransferItem.builder()
                                        .fileInfo(f)
                                        .from(from)
                                        .to(to)
                                        .build()
                        ));
                    }
                })
                .map(BaseModel::getId).collect(Collectors.toSet());

        // 查询目标位置的节点id，将源文件批量保存到目标位置下
        String targetNodeId = getNodeIdByPath(param.getTargetUid(), param.getTargetPath()).orElseThrow(() -> new JsonException(FileSystemError.FILE_NOT_FOUND, param.getTargetPath()));
        if (callback != null && callback.shouldInterrupt()) {
            return;
        }
        this.batchSaveFileInSameNode(param.getTargetUid(), targetNodeId, Boolean.TRUE.equals(param.getIsOverwrite()), sourceFileItemList);

        // 从待复制的源文件中筛选出目录，继续递归处理（挂载点除外）
        sourceFileItemList.stream().filter(f -> f.isDir() && !mountPointDirIdSet.contains(f.getId())).forEach(dir -> {
            if (callback != null && callback.shouldInterrupt()) {
                return;
            }
            String nextSourcePath = StringUtils.appendPath(param.getSourcePath(), dir.getName());
            String nextTargetPath = StringUtils.appendPath(param.getTargetPath(), dir.getName());
            if (callback != null) {
                if (callback.shouldInterrupt()) {
                    return;
                }
                callback.onAdditionalEvent(EnterDirEvent.of(
                        nextSourcePath,
                        nextTargetPath,
                        FileTransferItem.builder()
                                .from(nextSourcePath)
                                .to(nextTargetPath)
                                .fileInfo(dir)
                                .build()
                ));
            }
            SimpleFileTransferParam nextParam = SimpleFileTransferParam.builder()
                    .sourceUid(param.getSourceUid())
                    .sourcePath(nextSourcePath)
                    .targetUid(param.getTargetUid())
                    .targetPath(nextTargetPath)
                    .isOverwrite(param.getIsOverwrite())
                    .build();
            doCopy(nextParam, callback, depth + 1);
        });
    }

    /**
     *
     * 在同一个目录节点中批量新增文件信息。如果已存在同名文件，会根据isOverwrite策略判断是否覆盖。
     *
     * @param uid         文件所属的用户id
     * @param nodeId      文件所在目录路径的节点id
     * @param isOverwrite 是否覆盖同名文件。当已存在的文件与源文件不是同为文件 或 不是同为文件夹时，会抛出异常。
     * @param fileInfos   要批量新增的文件
     */
    private void batchSaveFileInSameNode(long uid, String nodeId, boolean isOverwrite, List<FileInfo> fileInfos) {
        if (fileInfos == null || fileInfos.isEmpty()) {
            return;
        }
        for (List<FileInfo> files : CollectionUtils.partition(fileInfos, 512)) {

            // 检查该目录下是否存在同名文件
            List<String> requireNames = files.stream().map(FileInfo::getName).toList();
            List<FileInfo> existFiles = fileInfoRepo.findFileInfoByNames(uid, requireNames, nodeId);
            Map<String, FileInfo> existFileMap = existFiles.stream().collect(Collectors.toMap(
                    FileInfo::getName,
                    Function.identity()
            ));
            List<FileInfo> toInsertFiles;
            if (isOverwrite) {
                // 检查当存在同名文件，但不是同为文件/目录则抛出异常
                files.stream()
                        .filter(f -> Optional.ofNullable(existFileMap.get(f.getName()))
                                .filter(ef -> ef.isDir() != f.isDir())
                                .isPresent()
                        )
                        .findAny()
                        .ifPresent(f -> {
                            throw new JsonException(f.isDir() ? FileSystemError.NOT_ALLOW_DIR_OVERWRITE_FILE : FileSystemError.NOT_ALLOW_FILE_OVERWRITE_DIR);
                        });

                if (existFileMap.isEmpty()) {
                    toInsertFiles = files;
                } else {
                    // 如果有同名文件，则先删除
                    List<String> existNames = existFiles.stream().filter(FileInfo::isFile).map(FileInfo::getName).toList();
                    if (!existNames.isEmpty()) {
                        fileInfoRepo.deleteFiles(uid, nodeId, existNames);
                    }

                    // 有同名的目录则需要排除，不操作
                    toInsertFiles = files.stream().filter(f -> {
                        if (f.isFile()) {
                            // 文件可直接保存（同名的前面已删除）
                            return true;
                        } else {
                            // 已存在的同名目录则不用动，只有不存在的目录才需要保存
                            return !existFileMap.containsKey(f.getName());
                        }
                    }).toList();
                }

            } else {
                toInsertFiles = files.stream().filter(f -> !existFileMap.containsKey(f.getName())).toList();
            }

            // 批量插入数据库
            for (FileInfo f : toInsertFiles) {
                f.setNode(nodeId);
                f.setUid(uid);
            }
            if (toInsertFiles.isEmpty()) {
                return;
            }
            DBUtils.batchInsert(jdbcTemplate, toInsertFiles);
        }
    }

    @Override
    public void batchSaveFileInSameDirectory(long uid, String path, boolean isOverwrite, List<FileInfo> fileInfos) {
        if (fileInfos == null || fileInfos.isEmpty()) {
            return;
        }

        // 先获取目录id，如果不存在则创建
        String nodeId = this.getAndMkdirs(uid, path, false);
        this.batchSaveFileInSameNode(uid, nodeId, isOverwrite, fileInfos);
    }

    /**
     * 在相同 uid 与 path 条件下批量保存文件记录，默认开启覆盖策略。
     *
     * @param uid 文件所属用户id
     * @param path 文件所在目录路径
     * @param fileInfos 待保存文件信息
     */
    @Override
    public void batchSaveFileInSameDirectory(long uid, String path, List<FileInfo> fileInfos) {
        this.batchSaveFileInSameDirectory(uid, path, true, fileInfos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(long sourceUid, String source, long targetUid, String target, String name, boolean overwrite) throws NoSuchFileException {
        String sourceNode = this.getNodeIdByPath(sourceUid, source).orElseThrow(() -> new JsonException(404, "原文件所在目录不存在"));
        String targetNode = this.getNodeIdByPath(targetUid, target).orElseThrow(() -> new JsonException(404, "目标文件所在目录不存在"));
        FileInfo sourceFileInfo = fileInfoRepo.findFileInfo(sourceUid, name, sourceNode);
        if (sourceFileInfo == null) {
            throw new NoSuchFileException("资源不存在，目录" + source + " 文件名：" + name);
        }
        FileInfo targetFileInfo;
        if (sourceUid != targetUid) {
            targetFileInfo = fileInfoRepo.findFileInfo(targetUid, name, targetNode);
        } else {
            targetFileInfo = fileInfoRepo.findFileInfo(sourceUid, name, targetNode);
        }

        if (sourceFileInfo.isDir()) {
            if (PathUtils.isSubDir(StringUtils.appendPath(source, name), StringUtils.appendPath(target, name))) {
                throw new JsonException("目标目录不能为源目录的子目录");
            }
            if (targetFileInfo != null) {
                if (targetFileInfo.getIsMount() != null || sourceFileInfo.getIsMount() != null) {
                    throw new JsonException("存在同名的挂载点目录\"" + name + "\"，无法移动:" + source + "->" + target);
                }
                if (targetFileInfo.isDir()) {
                    copy(SimpleFileTransferParam.builder()
                            .sourceUid(sourceUid)
                            .targetUid(targetUid)
                            .sourcePath(source)
                            .targetPath(target)
                            .files(List.of(name))
                            .isOverwrite(overwrite)
                            .build(), null);
                    deleteRecords(sourceUid, source, Collections.singleton(name));
                } else {
                    throw new JsonException("目标位置存在同名文件\"" + name + "\"，无法移动");
                }
            } else {
                if (sourceUid != targetUid) {
                    copy(SimpleFileTransferParam.builder()
                            .sourceUid(sourceUid)
                            .targetUid(targetUid)
                            .sourcePath(source)
                            .targetPath(target)
                            .files(List.of(name))
                            .isOverwrite(overwrite)
                            .build(), null);
                    deleteRecords(sourceUid, source, Collections.singleton(name));
                } else {
                    sourceFileInfo.setNode(targetNode);
                    fileInfoRepo.save(sourceFileInfo);
                }
            }
        } else {
            if (targetFileInfo != null) {
                if (targetFileInfo.isFile()) {
                    if (overwrite) {
                        targetFileInfo.setNode(sourceFileInfo.getNode());
                        fileInfoRepo.save(targetFileInfo);
                        fileInfoRepo.delete(sourceFileInfo);
                    }
                } else if (targetFileInfo.isDir()) {
                    throw new JsonException("目标位置存在同名目录\"" + name + "\"，无法移动");
                }
            } else {
                if (sourceUid != targetUid) {
                    sourceFileInfo.setUid(targetUid);
                }
                sourceFileInfo.setNode(targetNode);
                fileInfoRepo.save(sourceFileInfo);
            }
        }
    }

    @Override
    public FileInfo saveRecord(FileInfo fileInfo, String path) {
        if (fileInfo.getId() == null && fileInfo.getNode() == null) {
            String nodeId = getAndMkdirs(fileInfo.getUid(), path, Boolean.TRUE.equals(fileInfo.getIsMount()));
            fileInfo.setNode(nodeId);
        }
        FileInfo existFile;
        if (fileInfo.getId() != null) {
            existFile = fileInfoRepo.findById(fileInfo.getId()).orElse(null);
            if (existFile != null) {
                existFile.setCtime(fileInfo.getCtime());
                existFile.setMtime(fileInfo.getMtime());
                existFile.setMd5(fileInfo.getMd5());
                existFile.setSize(fileInfo.getSize());
                fileInfoRepo.save(existFile);
                return existFile;
            }
        } else {
            existFile = fileInfoRepo.findFileInfo(fileInfo.getUid(), fileInfo.getName(), fileInfo.getNode());
        }
        FileInfo newFile = FileInfo.createFrom(fileInfo, false);
        newFile.setUid(fileInfo.getUid());
        newFile.setNode(fileInfo.getNode());
        newFile.setIsMount(fileInfo.getIsMount());
        newFile.setMountId(fileInfo.getMountId());
        if (existFile != null) {
            newFile.setId(existFile.getId());
        }
        fileInfoRepo.save(newFile);
        return newFile;
    }

    @Override
    public FileInfo save(FileInfo fileInfo) {
        return fileInfoRepo.save(fileInfo);
    }

    @Override
    public List<FileInfo> findByUidAndNodeId(Long uid, String nodeId) {
        return fileInfoRepo.findByUidAndNode(uid, nodeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FileInfo> deleteRecords(long uid, String path, Collection<String> name) throws NoSuchFileException {
        String nodeId = this.getNodeIdByPath(uid, path).orElseThrow(() -> new JsonException("目录不存在"));
        List<FileInfo> infos = fileInfoRepo.findFileInfoByNames(uid, name, nodeId);
        List<FileInfo> res = new LinkedList<>();

        // 先将文件和文件夹分开并单独提取其文件名
        LinkedList<FileInfo> dirs = new LinkedList<>();
        LinkedList<String> files = new LinkedList<>();
        infos.forEach(info -> {
            if (info.isDir()) {
                dirs.push(info);
            } else {
                files.push(info.getName());
                res.add(info);
            }
        });

        dirs.forEach(dir -> res.addAll(deleteDirRecord(uid, dir)));
        if (files.size() != 0) {
            fileInfoRepo.deleteFiles(uid, nodeId, files);
        }
        return res;
    }

    @Override
    public String mkdir(long uid, String name, String path) throws NoSuchFileException {
        return doGetAndMkdirs(uid, StringUtils.appendPath(path, name), false);
    }

    /**
     * 新增一个目录类型的文件记录
     *
     * @param uid        用户id
     * @param name       目录名
     * @param parentNode 目录所在的目录节点
     * @param selfNodeId 目录自己的节点
     */
    private FileInfo addDirRecord(Long uid, String name, String parentNode, String selfNodeId, Boolean isMount) {
        FileInfo newFile = FileInfo.builder()
                .name(name)
                .md5(selfNodeId)
                .node(parentNode)
                .size(-1L)
                .isMount(isMount)
                .build();
        newFile.setUid(uid);
        long now = System.currentTimeMillis();
        newFile.setCtime(now);
        newFile.setMtime(now);
        return fileInfoRepo.saveAndFlush(newFile);
    }


    @Override
    public String mkdirs(long uid, String path, boolean isMount) {
        return doGetAndMkdirs(uid, path, isMount);
    }

    private String doMkdirs(long uid, String path, boolean isMount) {
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        // 起始的根目录节点id与用户id相同
        String parentNode = String.valueOf(uid);
        String nid = null;
        for (String name : pb.getPath()) {
            FileInfo fileInfo = getByParentId(uid, parentNode, name);
            if (fileInfo == null) {
                nid = SecureUtils.getUUID();
                addDirRecord(uid, name, parentNode, nid, isMount);
                parentNode = nid;
            } else {
                parentNode = fileInfo.getMd5();
            }
        }
        return nid;
    }

    private void safeSleep(int tryCount) {
        if (tryCount == 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(tryCount * 10, 200));
        } catch (InterruptedException ignore) {
        }
    }

    @Override
    public String getAndMkdirs(long uid, String path, boolean isMount) {
        return doGetAndMkdirs(uid, path, isMount);
    }


    private String doGetAndMkdirs(long uid, String path, boolean isMount) {
        Optional<String> quickQueryRes = this.getNodeIdByPath(uid, path);
        if (quickQueryRes.isPresent()) {
            return quickQueryRes.get();
        }

        String lockKey = "getAndMkdirs:" + uid + ":" + path;
        return LockUtils.execute(lockKey, () -> {
            int maxTryCount = 25;
            AtomicInteger tryCount = new AtomicInteger();
            while (tryCount.get() < maxTryCount) {
                String nodeId = DBUtils.executeWithTransactional(TransactionDefinition.PROPAGATION_REQUIRED, TransactionDefinition.ISOLATION_READ_COMMITTED, () -> {
                    // 双重检查
                    Optional<String> newestNodeId = this.getNodeIdByPath(uid, path);
                    if (newestNodeId.isPresent()) {
                        return newestNodeId.get();
                    }

                    try {
                        return doMkdirs(uid, path, isMount);
                    } catch (DataIntegrityViolationException e) {
                        boolean isDuplicate = false;
                        if (e instanceof DuplicateKeyException) {
                            isDuplicate = true;
                        } else if (e.getMostSpecificCause() instanceof SQLException se) {
                            // 23000 是 SQL 标准中代表 Integrity Constraint Violation 的状态码
                            // 1062 是 MySQL 特有的 Duplicate Entry 错误码
                            isDuplicate = "23000".equals(se.getSQLState()) || se.getErrorCode() == 1062;
                        }
                        if (!isDuplicate) {
                            throw e;
                        }
                        // 捕获唯一约束异常，重试查询
                        log.warn("getAndMkdirs并发创建冲突，重试查询: uid={}, path={}", uid, path);
                    }
                    return null;
                });
                if (nodeId != null) {
                    return nodeId;
                }
                safeSleep(tryCount.get());
                tryCount.incrementAndGet();
            }
            log.error("getAndMkdirs并发创建失败: uid={}, path={}", uid, path);
            throw new RuntimeException("getAndMkdir concurrent handle error");
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(long uid, String path, String oldName, String newName) throws NoSuchFileException {
        String nodeId = this.getNodeIdByPath(uid, path).orElseThrow(() -> new JsonException(404, "所在目录不存在"));
        FileInfo fileInfo = fileInfoRepo.findFileInfo(uid, oldName, nodeId);
        if (fileInfo == null) {
            throw new JsonException(404, "文件不存在");
        }
//        cacheService.deleteNodeCache(uid, Collections.singleton(nodeId));
        fileInfo.setName(newName);
        if (fileInfo.isDir()) {
            fileInfo.setMtime(System.currentTimeMillis());
        }
        fileInfoRepo.save(fileInfo);
    }

    /**
     * 删除一个文件夹下的所有文件记录
     *
     * @param uid     用户ID 0表示公共
     * @param dirInfo 文件夹信息
     * @return 被删除的文件信息（不包含文件夹）
     */
    private List<FileInfo> deleteDirRecord(long uid, FileInfo dirInfo) {
        List<FileInfo> res = new ArrayList<>();

        // 获取目录下的所有子目录信息
        List<FileInfo> childNodes = this.listChildDirs(uid, dirInfo.getMd5(), -1);
        List<String> nodeIdList = childNodes.stream().map(FileInfo::getMd5).collect(Collectors.toList());
        nodeIdList.add(dirInfo.getMd5());

        for (String nodeId : nodeIdList) {
            res.addAll(fileInfoRepo.findByUidAndNode(uid, nodeId));
        }
        fileInfoRepo.deleteByUidAndNode(uid, nodeIdList);
        fileInfoRepo.deleteFiles(uid, dirInfo.getNode(), Collections.singleton(dirInfo.getName()));
        return res;
    }

    @Override
    public void deleteFileInfo(long id) {
        FileInfo file = fileInfoRepo.getReferenceById(id);
        deleteFileInfo(file);
    }

    @Override
    public void deleteFileInfo(FileInfo fileInfo) {
        log.debug("{} 删除文件: {}", LOG_PREFIX, fileInfo.getName());
        if (fileInfo.isFile()) {
            fileInfoRepo.delete(fileInfo);
        } else {
            deleteDir(fileInfo, 1, 64);
        }
    }

    /**
     * 删除目录以及目录下的所有子项目
     *
     * @param fileInfo 待删除的目录信息
     * @param curDepth 当前递归深度
     * @param maxDepth 最大递归深度
     * @throws IllegalArgumentException 达到最大递归深度 maxDepth 时抛出
     */
    private void deleteDir(FileInfo fileInfo, int curDepth, int maxDepth) {
        if (curDepth >= maxDepth) {
            throw new IllegalArgumentException("达到最大递归深度: " + maxDepth + ", 请先手动删除较内层的数据");
        }
        List<FileInfo> subFileList = findByUidAndNodeId(fileInfo.getUid(), fileInfo.getMd5());
        if (subFileList.isEmpty()) {
            fileInfoRepo.delete(fileInfo);
            return;
        }
        // 将目录下的子项目按文件和目录进行分组处理
        Map<String, List<FileInfo>> typeGroup = subFileList.stream().collect(Collectors.groupingBy(e -> {
            if (e.isFile()) {
                return "file";
            } else {
                return "dir";
            }
        }));
        // 文件直接删除
        List<Long> fileIdList = typeGroup.getOrDefault("file", Collections.emptyList()).stream().map(BaseModel::getId).collect(Collectors.toList());
        if (!fileIdList.isEmpty()) {
            fileInfoRepo.batchDelete(fileIdList);
        }
        // 目录则需要递归处理
        List<FileInfo> dirList = typeGroup.get("dir");
        if (dirList != null) {
            for (FileInfo dir : dirList) {
                deleteDir(dir, curDepth + 1, maxDepth);
            }
        }
        fileInfoRepo.delete(fileInfo);
    }

    @Override
    public FileTree getFullTree(long uid) {
        FileTree tree = new FileTree();
        fileInfoRepo
                .findAll(JpaLambdaQueryWrapper.get(FileInfo.class)
                        .eq(FileInfo::getUid, uid)
                        .or(ow -> ow
                                .eq(FileInfo::getIsMount, false)
                                .isNull(FileInfo::getIsMount)
                        )
                        .eq(FileInfo::getSize, -1)
                        .build()
                )
                .forEach(tree::putNode);
        tree.putNode(FileInfo.getRoot(uid));
        return tree;
    }

    @Override
    public Long getUsage(long uid) {
        return fileInfoRepo.getFileUsage(uid);
    }

    @Override
    public Optional<String> getNodeIdByPath(long uid, String path) {
        return Optional.ofNullable(getVisitPathInfo(uid, path))
                .map(Deque::getLast)
                .map(FileInfo::getMd5);
    }

    @Override
    public Optional<FileInfo> getNodeByPath(long uid, String path) {
        return Optional.ofNullable(getVisitPathInfo(uid, path))
                .map(Deque::getLast);
    }

    @Override
    public Optional<String> getPathByNodeId(long uid, String nodeId) {
        return this.listAllParentByNodeId(uid, nodeId)
                .map(fileInfos -> "/" + fileInfos
                        .stream()
                        .map(FileInfo::getName)
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.joining("/"))
                );
    }

    @Override
    public Optional<Deque<FileInfo>> listAllParentByNodeId(long uid, String nodeId) {
        if (nodeId == null) {
            return Optional.empty();
        }
        ArrayDeque<FileInfo> res = new ArrayDeque<>();
        String prevNodeId = nodeId;
        String rootId = uid + "";
        while (prevNodeId != null && !rootId.equals(prevNodeId)) {
            FileInfo fileInfo = fileInfoRepo.findDirByUidAndMd5(uid, prevNodeId);
            if (fileInfo == null) {
                return Optional.empty();
            }
            res.addFirst(fileInfo);
            prevNodeId = fileInfo.getNode();
        }
        return Optional.of(res);
    }

    @Override
    public List<FileInfo> listChildDirs(long uid, String node, int depth) {
        int curDepth = 0;
        List<FileInfo> res = new ArrayList<>();
        List<String> needVisitNode = new ArrayList<>();
        needVisitNode.add(node);
        while (true) {
            if (needVisitNode.isEmpty()) {
                break;
            }

            // 准备下一个深度需要遍历的节点
            List<String> nextNeedVisitNode = new ArrayList<>();

            for (String curNode : needVisitNode) {
                Optional.ofNullable(fileInfoRepo.findDirByUidAndNode(uid, curNode))
                        .ifPresent(fileInfos -> {
                            // 记录本轮查询遍历结果
                            res.addAll(fileInfos);

                            // 将子目录节点id添加到待遍历列表
                            for (FileInfo fileInfo : fileInfos) {
                                if (fileInfo.isDir()) {
                                    nextNeedVisitNode.add(fileInfo.getMd5());
                                }
                            }
                        });
            }
            needVisitNode = nextNeedVisitNode;

            // 递归深度检测，小于0未无限制，这里只检测当depth >= 0的情况
            if (depth >= 0) {
                curDepth++;
                if (curDepth > depth) {
                    // 达到指定深度，跳出循环
                    break;
                }
            }
        }

        return res;
    }

    @Override
    public Optional<FileInfo> getByPath(long uid, String path) {
        return Optional.ofNullable(getVisitPathInfo(uid, path))
                .map(Deque::getLast);
    }

    @Override
    public FileInfo getByParentId(long uid, String parentId, String name) {
        return fileInfoRepo.findFileInfo(uid, name, parentId);
    }

    @Override
    public Deque<FileInfo> getVisitPathInfo(long uid, String path) {
        log.debug("{}<<==== 开始解析路径途径节点 uid: {} 路径: {}", LOG_PREFIX, uid, path);
        Deque<FileInfo> visitedPath = new ArrayDeque<>();
        Set<Long> visitedNodeIdSet = new HashSet<>();

        String strId = "" + uid;

        StringBuilder curPath = new StringBuilder();
        List<String> parts = new PathBuilder().append(path).getPath();
        int idx = 0;
        for (String dirName : parts) {
            idx++;
            String parentNodeId = visitedPath.isEmpty() ? strId : visitedPath.getLast().getMd5();
            FileInfo file = this.getByParentId(uid, parentNodeId, dirName);
            if (file == null) {
                log.warn("{}路径不存在:{}", LOG_PREFIX, path);
                return null;
            }
            if (idx != parts.size() && file.isFile()) {
                log.warn("{}路径中的节点为文件而不是目录: {}", LOG_PREFIX, file.getName());
                return null;
            }

            // 检测是否存在死循环
            if (visitedNodeIdSet.contains(file.getId())) {
                throw new JsonException(500, "出现文件夹循环包含，请联系管理员并提供以下信息：uid=" + uid + " " + file.getId() + " => " + dirName);
            } else {
                curPath.append("/").append(file.getName());
                file.setPath(curPath.toString());
                visitedNodeIdSet.add(file.getId());
                visitedPath.add(file);
            }
        }
        if (visitedPath.isEmpty()) {
            FileInfo info = new FileInfo();
            info.setId(uid);
            info.setUid(uid);
            info.setMd5(strId);
            info.setPath("/");
            info.setSize(-1L);
            visitedPath.add(info);
        }
        if (log.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            int cnt = 0;
            for (FileInfo file : visitedPath) {
                log.debug("{}途径节点[{}]: {}", LOG_PREFIX, cnt++, file);
                sb.append("/").append(file.getName() == null ? "" : file.getName()).append('[').append(file.getNode()).append(']');
            }
            log.debug("{}====>> 解析成功 路径节点信息: {}", LOG_PREFIX, sb);
        }
        return visitedPath;
    }
}
