package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.dao.mybatis.NodeDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.node.NodeTree;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 文件索引记录服务，管理数据库中的文件结构表
 */
@Service
@Slf4j
public class FileRecordServiceImpl implements FileRecordService {
    private final static String LOG_PREFIX = "[文件记录]";

    @Resource
    private NodeDao nodeDao;
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
    @Transactional(rollbackFor = Exception.class)
    public void copy(long uid, String source, String target, long targetId, String sourceName, String targetName, boolean overwrite) throws NoSuchFileException {
        // 原地复制，不处理
        if (uid == targetId && source.equals(target) && sourceName.equals(targetName)) {
            return;
        }
        PathBuilder pathBuilder = new PathBuilder();
        pathBuilder.setForcePrefix(true);
        int prefixLength = source.length() + 1 + sourceName.length();


        String sourceNodeId = this.getNodeIdByPath(uid, source).orElseThrow(() -> new JsonException(404, "源文件所在目录不存在"));
        FileInfo sourceInfo = fileInfoRepo.findFileInfo(uid, sourceName, sourceNodeId);
        if (sourceInfo == null) {
            throw new NoSuchFileException("文件 " + source + "/" + sourceName + " 不存在");
        }

        // 文件直接添加单条记录
        if (sourceInfo.isFile()) {
            FileInfo newRecord = FileInfo.createFrom(sourceInfo, false);
            newRecord.setUid(targetId);
            newRecord.setName(targetName);
            saveRecord(newRecord, target);
            return;
        }

        if (targetId == uid && sourceName.equals(targetName) && PathUtils.isSubDir(StringUtils.appendPath(source, sourceName), StringUtils.appendPath(target, targetName))) {
            throw new IllegalArgumentException("目标目录不能是源目录的子目录");
        }

        //  需要遍历的目录列表
        Deque<PathIdPair> needProcessSourceDirList = new ArrayDeque<>();
        String sourceRoot = StringUtils.appendPath(source, sourceName);
        needProcessSourceDirList.add(new PathIdPair(sourceRoot, this.getNodeIdByPath(uid, sourceRoot).orElseThrow(() -> new JsonException(404, "源文件所在目录不存在"))));

        do {
            //  更新目录列表
            PathIdPair pairInfo = needProcessSourceDirList.getLast();
            needProcessSourceDirList.removeLast();
            String newDirPath = target + "/" + targetName + "/" + pairInfo.path.substring(prefixLength);
            pathBuilder.update(newDirPath);
            PathIdPair newPathInfo;
            try {
                newPathInfo = new PathIdPair(newDirPath, mkdir(targetId, pathBuilder.getPathLast(), pathBuilder.range(-1)));
            } catch (DuplicateKeyException e) {
                newPathInfo = new PathIdPair(newDirPath, this.getNodeIdByPath(targetId, newDirPath).orElseThrow(() -> new JsonException(404, "源文件所在目录不存在")));
            }


            //  遍历一个目录并追加该目录下的子目录
            List<FileInfo> sourceFileList = fileInfoRepo.findByUidAndNode(uid, pairInfo.nid);
            for (FileInfo sourceFile : sourceFileList) {
                // 跳过挂载文件
                // todo 作为可选项
                if (Boolean.TRUE.equals(sourceFile.getIsMount()) || sourceFile.getMountId() != null) {
                    continue;
                }
                if (sourceFile.isDir()) {
                    needProcessSourceDirList.add(new PathIdPair(pairInfo.path + "/" + sourceFile.getName(), sourceFile.getMd5()));
                } else {
                    FileInfo existFile = fileInfoRepo.findFileInfo(targetId, sourceFile.getName(), newPathInfo.nid);
                    if (existFile == null) {
                        FileInfo newFile = FileInfo.createFrom(sourceFile, false);
                        newFile.setNode(newPathInfo.nid);
                        newFile.setUid(targetId);
                        fileInfoRepo.save(newFile);
                        log.debug("addFile: " + sourceFile.getName() + " at " + newPathInfo.nid);
                    } else {
                        if (overwrite) {
                            existFile.setMd5(sourceFile.getMd5());
                            existFile.setSize(sourceFile.getSize());
                            existFile.setCtime(sourceFile.getCtime());
                            existFile.setMtime(sourceFile.getMtime());
                            fileInfoRepo.save(existFile);
                            log.debug("overwrite " + sourceFile.getName() + " at " + newPathInfo.nid);
                        } else {
                            log.error("addFile failed: " + sourceFile.getName() + " at " + newPathInfo.nid);
                        }
                    }
                }
            }
        } while (!needProcessSourceDirList.isEmpty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(long uid, String source, String target, String name, boolean overwrite) throws NoSuchFileException {
        String sourceNode = this.getNodeIdByPath(uid, source).orElseThrow(() -> new JsonException(404, "原文件所在目录不存在"));
        String targetNode = this.getNodeIdByPath(uid, target).orElseThrow(() -> new JsonException(404, "目标文件所在目录不存在"));
        FileInfo sourceFileInfo = fileInfoRepo.findFileInfo(uid, name, sourceNode);
        if (sourceFileInfo == null) {
            throw new NoSuchFileException("资源不存在，目录" + source + " 文件名：" + name);
        }
        FileInfo targetFileInfo = fileInfoRepo.findFileInfo(uid, name, targetNode);

        if (sourceFileInfo.isDir()) {
            if (PathUtils.isSubDir(StringUtils.appendPath(source, name), StringUtils.appendPath(target, name))) {
                throw new JsonException("目标目录不能为源目录的子目录");
            }
            if (targetFileInfo != null) {
                if (targetFileInfo.getIsMount() != null || sourceFileInfo.getIsMount() != null) {
                    throw new JsonException("存在同名的挂载点目录\"" + name + "\"，无法移动:" + source + "->" + target);
                }
                // 当移动目录时存在同名文件或目录
                if (targetFileInfo.isDir()) {
                    // 目录 -> 目录 同名的是目录，则根据overwrite规则合并
                    copy(uid, source, target, uid, name, name, overwrite);
                    deleteRecords(uid, source, Collections.singleton(name));
                } else {
                    // 目录 -> 文件 同名的是文件，不支持的操作，需要手动解决
                    throw new JsonException("目标位置存在同名文件\"" + name + "\"，无法移动");
                }
            } else {
                // 不存在同名目录，直接修改节点ID
//                cacheService.deleteNodeCache(uid, Collections.singleton(sourceFileInfo.getMd5()));
                sourceFileInfo.setNode(targetNode);
                fileInfoRepo.save(sourceFileInfo);
                nodeDao.move(uid, sourceFileInfo.getMd5(), targetNode);
            }
        } else {
            if (targetFileInfo != null) {
                // 当移动文件时存在同名文件或目录
                if (targetFileInfo.isFile()) {
                    // 文件 -> 文件，覆盖/删除
                    if (overwrite) {
                        targetFileInfo.setNode(sourceFileInfo.getNode());
                        fileInfoRepo.save(targetFileInfo);
                        fileInfoRepo.delete(sourceFileInfo);
                    }
                } else if (targetFileInfo.isDir()) {
                    // 文件 -> 目录 不支持的操作，需要手动解决
                    throw new JsonException("目标位置存在同名目录\"" + name + "\"，无法移动");
                }
            } else {
                // 不存在同名文件，直接修改文件所属节点ID
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
    @Transactional(rollbackFor = Exception.class)
    public String mkdir(long uid, String name, String path) throws NoSuchFileException {
        log.debug("mkdir " + name + " at " + path);
        String nodeId = this.getNodeIdByPath(uid, path).orElseThrow(() -> new JsonException("上级目录不存在"));
        String newNodeId = SecureUtils.getUUID();
        FileInfo existFile = fileInfoRepo.findFileInfo(uid, name, nodeId);
        if (existFile != null) {
            throw new DuplicateKeyException("目录已存在");
        }
        addDirRecord(uid, name, nodeId, newNodeId, false);
        log.debug("mkdir finish: " + newNodeId);
        return newNodeId;
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
        return fileInfoRepo.save(newFile);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdirs(long uid, String path, boolean isMount) {
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        // 起始的根目录节点id与用户id相同
        String parentNode = String.valueOf(uid);
        String nid = null;
        for (String name : pb.getPath()) {
            NodeInfo nodeInfo = nodeDao.getNodeByParentId(uid, parentNode, name);
            if (nodeInfo == null) {
                nid = SecureUtils.getUUID();
                addDirRecord(uid, name, parentNode, nid, isMount);
                parentNode = nid;
            } else {
                parentNode = nodeInfo.getId();
            }
        }
        return nid;
    }

    @Override
    public String getAndMkdirs(long uid, String path, boolean isMount) {
        Optional<String> quickQueryRes = this.getNodeIdByPath(uid, path);
        if (quickQueryRes.isPresent()) {
            return quickQueryRes.get();
        }

        String lockKey = "getAndMkdirs:" + uid + ":" + path;
        return LockUtils.execute(lockKey, () -> DBUtils.executeWithTransactional(TransactionDefinition.PROPAGATION_REQUIRES_NEW, () -> {
            int interval = 200;
            int maxTryCount = 60 * 1000 / interval;
            int tryCount = 0;
            while (tryCount < maxTryCount) {
                // 双重检查
                Optional<String> newestNodeId = this.getNodeIdByPath(uid, path);
                if (newestNodeId.isPresent()) {
                    return newestNodeId.get();
                }

                try {
                    return mkdirs(uid, path, isMount);
                } catch (DuplicateKeyException e) {
                    // 捕获唯一约束异常，重试查询
                    log.warn("getAndMkdirs并发创建冲突，重试查询: uid={}, path={}", uid, path);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignore) {
                    }
                }
                tryCount++;
            }
            log.error("getAndMkdirs并发创建失败: uid={}, path={}", uid, path);
            throw new RuntimeException("getAndMkdir concurrent handle error");
        }));
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
        if (fileInfo.isDir()) {
            nodeDao.changeName(uid, nodeDao.getNodeByParentId(uid, nodeId, oldName).getId(), newName);
        }
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
    public NodeTree getFullTree(long uid) {
        NodeTree tree = new NodeTree();
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
            String parentNodeId = visitedPath.isEmpty() ? strId : visitedPath.getLast().getNode();
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
            info.setNode(strId);
            info.setPath("/");
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
