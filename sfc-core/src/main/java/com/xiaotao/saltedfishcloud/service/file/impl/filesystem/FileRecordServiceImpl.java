package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.dao.mybatis.NodeDao;
import com.xiaotao.saltedfishcloud.entity.po.NodeInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.node.cache.NodeCacheService;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.nio.file.NoSuchFileException;
import java.util.*;


/**
 * 文件索引记录服务，管理数据库中的文件结构表
 */
@Service
@Slf4j
public class FileRecordServiceImpl implements FileRecordService {
    @Resource
    private FileDao fileDao;

    @Resource
    private NodeService nodeService;

    @Resource
    private NodeDao nodeDao;
    @Resource
    private NodeCacheService cacheService;

    private static class PathIdPair {
        public String path;
        public String nid;
        public PathIdPair(String path, String nid) {this.path = path;this.nid = nid;}
    }

    @Override
    public FileInfo getFileInfo(int uid, String dirPath, String name) throws NoSuchFileException {
        final String nodeId = nodeService.getNodeIdByPath(uid, dirPath);
        final FileInfo info = fileDao.getFileInfo(uid, name, nodeId);
        if (info == null) {
            throw new NoSuchFileException(StringUtils.appendPath(dirPath, name));
        } else {
            return info;
        }
    }

    @Override
    public boolean exist(int uid, String path, String name) {
        try {
            final String nid = nodeService.getNodeIdByPath(uid, path);
            if ((uid + "").equals(nid) && (name == null || name.length() == 0)) {
                return true;
            }
            return fileDao.getFileInfo(uid, name, nid) != null;
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    @Override
    public List<FileInfo> getFileInfoByMd5(String md5, int limit) {
        return fileDao.getFilesByMD5(md5, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, boolean overwrite) throws NoSuchFileException {
        PathBuilder pathBuilder = new PathBuilder();
        pathBuilder.setForcePrefix(true);
        int prefixLength = source.length() + 1 + sourceName.length();


        FileInfo sourceInfo = fileDao.getFileInfo(uid, sourceName, nodeService.getNodeIdByPath(uid, source));
        if (sourceInfo == null) throw new NoSuchFileException("文件 " + source + "/" + sourceName + " 不存在");
        // 文件直接添加单条记录
        if (sourceInfo.isFile()) {
            addRecord(targetId, targetName, sourceInfo.getSize(), sourceInfo.getMd5(), target);
            return ;
        }

        if (targetId == uid && sourceName.equals(targetName) && PathUtils.isSubDir(source + "/" + sourceName, target + "/" + targetName)) {
            throw new IllegalArgumentException("目标目录不能是源目录的子目录");
        }

        //  需要遍历的目录列表
        LinkedList<PathIdPair> t = new LinkedList<>();
        String sourceRoot = source + "/" + sourceName;
        t.add(new PathIdPair(sourceRoot, nodeService.getNodeIdByPath(uid, sourceRoot)));

        do {
            //  更新目录列表
            PathIdPair pairInfo = t.getLast();
            t.removeLast();
            String newDirPath = target + "/" + targetName + "/" + pairInfo.path.substring(prefixLength);
            pathBuilder.update(newDirPath);
            PathIdPair newPathInfo;
            try {
                newPathInfo = new PathIdPair(newDirPath, mkdir(targetId, pathBuilder.getPath().getLast(), pathBuilder.range(-1)));
            } catch (DuplicateKeyException e) {
                newPathInfo = new PathIdPair(newDirPath, nodeService.getNodeIdByPath(targetId, newDirPath));
            }


            //  遍历一个目录并追加该目录下的子目录
            List<FileInfo> t2 = fileDao.getFileListByNodeId(uid, pairInfo.nid);
            for (FileInfo info : t2) {
                if (info.isDir()) {
                    t.add(new PathIdPair(pairInfo.path + "/" + info.getName(), info.getMd5()));
                } else {
                    if (fileDao.addRecord(targetId, info.getName(), info.getSize(), info.getMd5(), newPathInfo.nid) < 1 && overwrite) {
                        fileDao.updateRecord(targetId, info.getName(), newPathInfo.nid, info.getSize(), info.getMd5());
                        log.debug("overwrite " + info.getName() + " at " + newPathInfo.nid);
                    } else if (!overwrite){
                        log.error("addFile failed: " + info.getName() + " at " + newPathInfo.nid);
                    } else {
                        log.debug("addFile: " + info.getName() + " at " + newPathInfo.nid);
                    }
                }
            }
        } while (t.size() > 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(int uid, String source, String target, String name, boolean overwrite) throws NoSuchFileException {
        String sourceId = nodeService.getNodeIdByPath(uid, source);
        String targetId = nodeService.getNodeIdByPath(uid, target);
        FileInfo sourceFileInfo = fileDao.getFileInfo(uid, name, sourceId);
        if (sourceFileInfo == null) {
            throw new NoSuchFileException("资源不存在，目录" + source + " 文件名：" + name);
        }
        FileInfo targetFileInfo = fileDao.getFileInfo(uid, name, targetId);

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
                fileDao.move(uid, sourceId, targetId, name);
                nodeDao.move(uid, sourceFileInfo.getMd5(), targetId);
            }
        } else {
            if (targetFileInfo != null) {
                // 当移动文件时存在同名文件或目录
                if (targetFileInfo.isFile()) {
                    // 文件 -> 文件，覆盖/删除
                    if (overwrite) {
                        fileDao.updateRecord(uid, name, targetFileInfo.getNode(), sourceFileInfo.getSize(), sourceFileInfo.getMd5());
                    }
                    fileDao.deleteRecords(uid, sourceFileInfo.getNode(), Collections.singletonList(name));
                } else if (targetFileInfo.isDir()){
                    // 文件 -> 目录 不支持的操作，需要手动解决
                    throw new UnsupportedOperationException("目标位置存在同名目录\"" + name  + "\"，无法移动");
                }
            } else {
                // 不存在同名文件，直接修改文件所属节点ID
                fileDao.move(uid, sourceFileInfo.getNode(), targetId, name);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addRecord(int uid, String name, Long size, String md5, String path) throws NoSuchFileException {
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        return fileDao.addRecord(uid, name, size, md5, nodeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateFileRecord(int uid, String name, String path, Long newSize, String newMd5) throws NoSuchFileException {
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        return fileDao.updateRecord(uid, name, nodeId, newSize, newMd5);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FileInfo> deleteRecords(int uid, String path, Collection<String> name) throws NoSuchFileException {
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        List<FileInfo> infos = fileDao.getFilesInfo(uid, name, nodeId);
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
            fileDao.deleteRecords(uid, nodeId, files);
        }
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdir(int uid, String name, String path) throws NoSuchFileException {
        log.debug("mkdir " + name + " at " + path);
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        String newNodeId = nodeService.addNode(uid, name, nodeId);
        if (fileDao.addRecord(uid, name, -1L, newNodeId, nodeId) < 1) {
            throw new DuplicateKeyException("目录已存在");
        }
        fileDao.addRecord(uid, name, -1L, newNodeId, nodeId);
        log.debug("mkdir finish: " + newNodeId);
        return newNodeId;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String mkdirs(int uid, String path) {
        PathBuilder pb = new PathBuilder();
        pb.append(path);
        String id = "" + uid;
        String nid = null;
        for (String s : pb.getPath()) {
            NodeInfo nodeInfo = nodeDao.getNodeByParentId(uid, id, s);
            if (nodeInfo == null) {
                nid = nodeService.addNode(uid, s, id);
                fileDao.addRecord(uid, s, -1L, nid, id);
                id = nid;
            } else {
                id = nodeInfo.getId();
            }
        }
        return nid;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(int uid, String path, String oldName, String newName) throws NoSuchFileException, JsonProcessingException {
        String nodeId = nodeService.getNodeIdByPath(uid, path);
        FileInfo fileInfo = fileDao.getFileInfo(uid, oldName, nodeId);
        if (fileInfo == null) {
            throw new JsonException(404, "文件不存在");
        }
        cacheService.deleteNodeCache(uid, Collections.singleton(nodeId));
        if (fileInfo.isDir()) {
            nodeDao.changeName(uid, nodeDao.getNodeByParentId(uid, nodeId, oldName).getId(), newName);
        }
        fileDao.rename(uid, nodeId, oldName, newName);
    }

    /**
     * 删除一个文件夹下的所有文件记录
     * @param uid   用户ID 0表示公共
     * @param dirInfo   文件夹信息
     * @return 被删除的文件信息（不包含文件夹）
     */
    private List<FileInfo> deleteDirRecord(int uid, FileInfo dirInfo) {
         List<FileInfo> res = new LinkedList<>();

        // 目录下的所有子目录信息
        List<NodeInfo> childNodes = nodeService.getChildNodes(uid, dirInfo.getMd5());
        List<String> ids = new LinkedList<>();
        ids.add(dirInfo.getMd5());

        // 从节点集合中提取出节点ID集合
        childNodes.forEach(nodeInfo -> ids.add(nodeInfo.getId()));
        nodeService.deleteNodes(uid, ids);
        if (!ids.isEmpty()) {
            for (String e : ids) {
                res.addAll(fileDao.getFileListByNodeId(uid, e));
            }
            fileDao.deleteDirsRecord(uid, ids);
        }
        fileDao.deleteRecords(uid, dirInfo.getParent(), Collections.singletonList(dirInfo.getName()));
        return res;
    }
}
