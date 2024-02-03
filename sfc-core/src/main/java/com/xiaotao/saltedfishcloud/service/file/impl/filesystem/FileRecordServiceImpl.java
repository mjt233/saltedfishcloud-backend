package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.dao.mybatis.NodeDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.model.po.NodeInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.node.cache.NodeCacheService;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 文件索引记录服务，管理数据库中的文件结构表
 */
@Service
@Slf4j
public class FileRecordServiceImpl implements FileRecordService {

    @Resource
    private NodeService nodeService;

    @Resource
    private NodeDao nodeDao;
    @Resource
    private NodeCacheService cacheService;

    @Resource
    private FileInfoRepo fileInfoRepo;

    private static class PathIdPair {
        public String path;
        public String nid;
        public PathIdPair(String path, String nid) {this.path = path;this.nid = nid;}
    }

    @Override
    public FileInfo getFileInfo(long uid, String dirPath, String name) {
        String requestDir = dirPath;
        String requestName = name;
        if (name == null) {
            requestDir = PathUtils.getParentPath(dirPath);
            requestName = PathUtils.getLastNode(dirPath);
        }
        final String nodeId = nodeService.getNodeIdByPathNoEx(uid, requestDir);
        if (nodeId == null) {
            return null;
        }
        return fileInfoRepo.findFileInfo(uid, requestName, nodeId);
    }

    @Override
    public FileInfo getFileInfoByNode(long uid, String nid, String name) {
        return fileInfoRepo.findFileInfo(uid, name, nid);
    }

    @Override
    public boolean exist(long uid, String path, String name) {
        try {
            final String nid = nodeService.getNodeIdByPath(uid, path);
            if ((uid + "").equals(nid) && (name == null || name.length() == 0)) {
                return true;
            }
            return fileInfoRepo.findFileInfo(uid, name, nid) != null;
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    @Override
    public List<FileInfo> getFileInfoByMd5(String md5, int limit) {
        Page<FileInfo> page = fileInfoRepo.findByMd5(md5, PageRequest.of(0, limit));
        return page.getContent();
    }

    @Override
    public List<FileInfo> findByUidAndNodeId(Long uid, String nodeId,@Nullable Collection<String> nameList) {
        if (nameList == null || nameList.isEmpty()) {
            return fileInfoRepo.findFileInfoByNames(uid, nameList, nodeId);
        } else {
            return findByUidAndNodeId(uid, nodeId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copy(long uid, String source, String target, long targetId, String sourceName, String targetName, boolean overwrite) throws NoSuchFileException {
        PathBuilder pathBuilder = new PathBuilder();
        pathBuilder.setForcePrefix(true);
        int prefixLength = source.length() + 1 + sourceName.length();


        FileInfo sourceInfo = fileInfoRepo.findFileInfo(uid, sourceName, nodeService.getNodeIdByPath(uid, source));
        if (sourceInfo == null) throw new NoSuchFileException("文件 " + source + "/" + sourceName + " 不存在");
        // 文件直接添加单条记录
        if (sourceInfo.isFile()) {
            FileInfo newRecord = FileInfo.createFrom(sourceInfo, false);
            newRecord.setUid(targetId);
            newRecord.setName(targetName);
            newRecord.setNode(null);
            saveRecord(newRecord, target);
            return ;
        }

        if (targetId == uid && sourceName.equals(targetName) && PathUtils.isSubDir(source + "/" + sourceName, target + "/" + targetName)) {
            throw new IllegalArgumentException("目标目录不能是源目录的子目录");
        }

        //  需要遍历的目录列表
        Deque<PathIdPair> needProcessSourceDirList = new ArrayDeque<>();
        String sourceRoot = source + "/" + sourceName;
        needProcessSourceDirList.add(new PathIdPair(sourceRoot, nodeService.getNodeIdByPath(uid, sourceRoot)));

        do {
            //  更新目录列表
            PathIdPair pairInfo = needProcessSourceDirList.getLast();
            needProcessSourceDirList.removeLast();
            String newDirPath = target + "/" + targetName + "/" + pairInfo.path.substring(prefixLength);
            pathBuilder.update(newDirPath);
            PathIdPair newPathInfo;
            try {
                newPathInfo = new PathIdPair(newDirPath, mkdir(targetId, pathBuilder.getPath().getLast(), pathBuilder.range(-1)));
            } catch (DuplicateKeyException e) {
                newPathInfo = new PathIdPair(newDirPath, nodeService.getNodeIdByPath(targetId, newDirPath));
            }


            //  遍历一个目录并追加该目录下的子目录
            List<FileInfo> sourceFileList = fileInfoRepo.findByUidAndNode(uid, pairInfo.nid);
            for (FileInfo sourceFile : sourceFileList) {
                if (sourceFile.isDir()) {
                    needProcessSourceDirList.add(new PathIdPair(pairInfo.path + "/" + sourceFile.getName(), sourceFile.getMd5()));
                } else {
                    FileInfo existFile = fileInfoRepo.findFileInfo(targetId, sourceFile.getName(), newPathInfo.nid);
                    if (existFile == null) {
                        FileInfo newFile = FileInfo.createFrom(sourceFile, false);
                        newFile.setNode(newPathInfo.nid);
                        newFile.setId(null);
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
        } while (needProcessSourceDirList.size() > 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(long uid, String source, String target, String name, boolean overwrite) throws NoSuchFileException {
        String sourceNode = nodeService.getNodeIdByPath(uid, source);
        String targetNode = nodeService.getNodeIdByPath(uid, target);
        FileInfo sourceFileInfo = fileInfoRepo.findFileInfo(uid, name, sourceNode);
        if (sourceFileInfo == null) {
            throw new NoSuchFileException("资源不存在，目录" + source + " 文件名：" + name);
        }
        FileInfo targetFileInfo = fileInfoRepo.findFileInfo(uid, name, targetNode);

        if (sourceFileInfo.isDir()) {
            if (PathUtils.isSubDir(source + "/" + name, target + "/" + name)) {
                throw new IllegalArgumentException("目标目录不能为源目录的子目录");
            }
            if (targetFileInfo != null) {
                // 当移动目录时存在同名文件或目录
                if (targetFileInfo.isDir()) {
                    // 目录 -> 目录 同名的是目录，则根据overwrite规则合并
                    copy(uid, source, target, uid, name, name, overwrite);
                    deleteRecords(uid, source, Collections.singleton(name));
                } else {
                    // 目录 -> 文件 同名的是文件，不支持的操作，需要手动解决
                    throw new UnsupportedOperationException("目标位置存在同名文件\"" + name  + "\"，无法移动");
                }
            } else {
                // 不存在同名目录，直接修改节点ID
                cacheService.deleteNodeCache(uid, Collections.singleton(sourceFileInfo.getMd5()));
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
                } else if (targetFileInfo.isDir()){
                    // 文件 -> 目录 不支持的操作，需要手动解决
                    throw new UnsupportedOperationException("目标位置存在同名目录\"" + name  + "\"，无法移动");
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
            String nodeId = nodeService.getNodeIdByPathNoEx(fileInfo.getUid(), path);
            fileInfo.setNode(nodeId);
        }
        return fileInfoRepo.save(fileInfo);
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
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        List<FileInfo> infos = fileInfoRepo.findFileInfoByNames(uid, name, nodeId);
        List<FileInfo> res = new LinkedList<>();

        // 先将文件和文件夹分开并单独提取其文件名
        LinkedList<FileInfo> dirs = new LinkedList<>();
        LinkedList<String> files = new LinkedList<>();
        infos.forEach(info -> {
            if( info.isDir()) {
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
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        String newNodeId = nodeService.addNode(uid, name, nodeId);
        FileInfo existFile = fileInfoRepo.findFileInfo(uid, name, nodeId);
        if (existFile != null) {
            throw new DuplicateKeyException("目录已存在");
        }
        addDirRecord(uid, name, nodeId, newNodeId);
        log.debug("mkdir finish: " + newNodeId);
        return newNodeId;
    }

    /**
     * 新增一个目录类型的文件记录
     * @param uid           用户id
     * @param name          目录名
     * @param node          目录所在的目录节点
     * @param selfNodeId    目录自己的节点
     */
    private FileInfo addDirRecord(Long uid, String name, String node, String selfNodeId) {
        FileInfo newFile = FileInfo.builder()
                .name(name)
                .md5(selfNodeId)
                .node(node)
                .size(-1L)
                .build();
        newFile.setUid(uid);
        long now = System.currentTimeMillis();
        newFile.setCtime(now);
        newFile.setMtime(now);
        return fileInfoRepo.save(newFile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdirs(long uid, String path) {
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        // 起始的根目录节点id与用户id相同
        String parentNode = String.valueOf(uid);
        String nid = null;
        for (String name : pb.getPath()) {
            NodeInfo nodeInfo = nodeDao.getNodeByParentId(uid, parentNode, name);
            if (nodeInfo == null) {
                nid = nodeService.addNode(uid, name, parentNode);
                addDirRecord(uid, name, parentNode, nid);
                parentNode = nid;
            } else {
                parentNode = nodeInfo.getId();
            }
        }
        return nid;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(long uid, String path, String oldName, String newName) throws NoSuchFileException {
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        FileInfo fileInfo = fileInfoRepo.findFileInfo(uid, oldName, nodeId);
        if (fileInfo == null) {
            throw new JsonException(404, "文件不存在");
        }
        cacheService.deleteNodeCache(uid, Collections.singleton(nodeId));
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
     * @param uid   用户ID 0表示公共
     * @param dirInfo   文件夹信息
     * @return 被删除的文件信息（不包含文件夹）
     */
    private List<FileInfo> deleteDirRecord(long uid, FileInfo dirInfo) {
         List<FileInfo> res = new ArrayList<>();

        // 目录下的所有子目录信息
        List<NodeInfo> childNodes = nodeService.getChildNodes(uid, dirInfo.getMd5());
        List<String> nodeIdList = childNodes.stream().map(NodeInfo::getId).collect(Collectors.toList());
        nodeIdList.add(dirInfo.getMd5());

        nodeService.deleteNodes(uid, nodeIdList);
        if (!nodeIdList.isEmpty()) {
            for (String nodeId : nodeIdList) {
                res.addAll(fileInfoRepo.findByUidAndNode(uid, nodeId));
            }
            fileInfoRepo.deleteByUidAndNode(uid, nodeIdList);
        }
        fileInfoRepo.deleteFiles(uid, dirInfo.getNode(), Collections.singleton(dirInfo.getName()));
        return res;
    }
}
