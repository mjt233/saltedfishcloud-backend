package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.dao.NodeDao;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * 文件索引记录服务，管理数据库中的文件结构表
 */
@Service
@Slf4j
public class FileRecordService {
    @Resource
    private FileDao fileDao;

    @Resource
    private NodeService nodeService;

    @Resource
    private NodeDao nodeDao;

    @Resource
    private FileService fileService;

    /**
     * 操作数据库复制网盘文件或目录到指定目录下
     * @param uid       用户ID
     * @param source    要复制的文件或目录所在目录
     * @param target    复制到的目标目录
     * @param targetId  复制到的目标目录所属用户ID
     * @param sourceName      要复制的文件或目录名
     * @param overwrite 是否覆盖已存在的文件
     */
    @Transactional(rollbackFor = Exception.class)
    public void copy(int uid, String source, String target, int targetId, String sourceName, String targetName, boolean overwrite) throws NoSuchFileException {
        class PathIdPair {
            public String path;
            public String nid;
            public PathIdPair(String path, String nid) {this.path = path;this.nid = nid;}
        }
        PathBuilder pathBuilder = new PathBuilder();
        pathBuilder.setForcePrefix(true);
        int prefixLength = source.length() + 1 + sourceName.length();

        FileInfo sourceInfo = fileDao.getFileInfo(uid, sourceName, nodeService.getLastNodeInfoByPath(uid, source).getId());
        if (sourceInfo == null) throw new NoSuchFileException("文件 " + source + "/" + sourceName + " 不存在");
        // 文件直接添加单条记录
        if (sourceInfo.isFile()) {
            addRecord(targetId, targetName, sourceInfo.getSize(), sourceInfo.getMd5(), target);
            return ;
        }

        //  需要遍历的目录列表
        LinkedList<PathIdPair> t = new LinkedList<>();
        String sourceRoot = source + "/" + sourceName;
        t.add(new PathIdPair(sourceRoot, nodeService.getLastNodeInfoByPath(uid, sourceRoot).getId()));

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
                newPathInfo = new PathIdPair(newDirPath, nodeService.getLastNodeInfoByPath(targetId, newDirPath).getId());
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

    /**
     * 操作数据库移动网盘文件或目录到指定目录下
     * @param uid       用户ID
     * @param source    网盘文件或目录所在目录
     * @param target    网盘目标目录
     * @param name      文件名
     * @param overwrite 是否覆盖原文件信息
     * @throws NoSuchFileException 当原目录或目标目录不存在时抛出
     */
    public void move(int uid, String source, String target, String name, boolean overwrite) throws NoSuchFileException {
        NodeInfo sourceInfo = nodeService.getLastNodeInfoByPath(uid, source);
        NodeInfo targetInfo = nodeService.getLastNodeInfoByPath(uid, target);
        FileInfo sourceFileInfo = fileDao.getFileInfo(uid, name, sourceInfo.getId());
        FileInfo targetFileInfo = fileDao.getFileInfo(uid, name, targetInfo.getId());

        if (sourceFileInfo.isDir()) {
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
                fileDao.move(uid, sourceInfo.getId(), targetInfo.getId(), name);
                nodeDao.move(uid, sourceFileInfo.getMd5(), targetInfo.getId());
            }
        } else {
            if (targetFileInfo != null) {
                // 当移动文件时存在同名文件或目录
                if (targetFileInfo.isFile()) {
                    // 文件 -> 文件，覆盖/删除
                    if (overwrite) {
                        fileDao.updateRecord(uid, name, targetFileInfo.getNode(), sourceFileInfo.getSize(), sourceFileInfo.getMd5());
                    }
                    fileDao.deleteRecord(uid, sourceFileInfo.getNode(), Collections.singletonList(name));
                } else if (targetFileInfo.isDir()){
                    // 文件 -> 目录 不支持的操作，需要手动解决
                    throw new UnsupportedOperationException("目标位置存在同名目录\"" + name  + "\"，无法移动");
                }
            } else {
                // 不存在同名文件，直接修改文件所属节点ID
                fileDao.move(uid, sourceFileInfo.getNode(), targetInfo.getId(), name);
            }
        }
    }

    /**
     * 添加一个记录
     * @param uid   用户ID 0表示公共
     * @param name  文件名
     * @param size  文件大小
     * @param md5   文件MD5
     * @param path  文件所在路径
     * @return 添加数量
     */
    public int addRecord(int uid, String name, Long size, String md5, String path) throws NoSuchFileException {
        NodeInfo node = nodeService.getLastNodeInfoByPath(uid, path);
        return fileDao.addRecord(uid, name, size, md5, node.getId());
    }

    /**
     * 因文件被替换而更新一条记录
     * @param uid   用户ID 0表示公共
     * @param name  文件名
     * @param path  文件所在路径
     * @param newSize 新的文件大小
     * @param newMd5 新的文件MD5
     * @return 影响行数
     */
    int updateFileRecord(int uid, String name, String path, Long newSize, String newMd5) throws NoSuchFileException {
        NodeInfo node = nodeService.getLastNodeInfoByPath(uid, path);
        return fileDao.updateRecord(uid, name, node.getId(), newSize, newMd5);
    }

    /**
     * 批量删除某个目录下的文件或文件夹，文件夹的所有子文件夹和文件也会被一同删除
     * @param uid   用户ID 0表示公共
     * @param path  路径
     * @param name  文件名列表
     * @return 删除的文件个数
     */
    public int deleteRecords(int uid, String path, Collection<String> name) throws NoSuchFileException {
        NodeInfo node = nodeService.getLastNodeInfoByPath(uid, path);
        List<FileInfo> infos = fileDao.getFilesInfo(uid, name, node.getId());

        // 先将文件和文件夹分开并单独提取其文件名
        LinkedList<FileInfo> dirs = new LinkedList<>();
        LinkedList<String> files = new LinkedList<>();
        infos.forEach(info -> {
            if( info.isDir()) {
                dirs.push(info);
            } else {
                files.push(info.getName());
            }
        });
        AtomicInteger cnt = new AtomicInteger();

        dirs.forEach(dir -> cnt.addAndGet(deleteDirRecord(uid, dir)));
        if (files.size() != 0) {
            cnt.addAndGet(fileDao.deleteRecord(uid, node.getId(), files));
        }
        return cnt.get();
    }

    /**
     * 向数据库系统新建一个文件夹记录
     * @param uid   用户ID
     * @param name  文件夹名称
     * @param path  所在路径
     * @throws DuplicateKeyException
     *      当目标目录已存在时抛出
     * @throws NoSuchFileException
     *      当父级目录不存在时抛出
     */
    public String mkdir(int uid, String name, String path) throws NoSuchFileException {
        log.debug("mkdir " + name + " at " + path);
        NodeInfo node = nodeService.getLastNodeInfoByPath(uid, path);
        String nodeId = nodeService.addNode(uid, name, node.getId());
        if (fileDao.addRecord(uid, name, -1L, nodeId, node.getId()) < 1) {
            throw new DuplicateKeyException("目录已存在");
        }
        fileDao.addRecord(uid, name, -1L, nodeId, node.getId());
        log.debug("mkdir finish: " + nodeId);
        return nodeId;
    }

    /**
     * 对文件或文件夹进行重命名
     * @param uid   用户ID
     * @param path  目标文件或文件夹所在路径
     * @param oldName  旧文件名
     * @param newName 新文件名
     */
    public void rename(int uid, String path, String oldName, String newName) throws NoSuchFileException {
        NodeInfo pathNodeInfo = nodeService.getLastNodeInfoByPath(uid, path);
        FileInfo fileInfo = fileDao.getFileInfo(uid, oldName, pathNodeInfo.getId());
        if (fileInfo.isDir()) {
            nodeDao.changeName(uid, nodeDao.getNodeByParentId(uid, pathNodeInfo.getId(), oldName).getId(), newName);
        }
        fileDao.rename(uid, pathNodeInfo.getId(), oldName, newName);
    }

    /**
     * 将本地公共网盘的文件信息写入数据库
     */
    public void makePublicRecord() throws IOException {
        DirCollection dirCollection = FileUtils.scanDir(DiskConfig.getRawFileStoreRootPath(0));
        AtomicLong atomicLong = new AtomicLong();
        atomicLong.set(0);
        long total = dirCollection.getDirsCount();

        // 先创建目录
        long finalTotal = total;
        for (File file1 : dirCollection.getDirList()) {
            String path = PathUtils.getRelativePath(0, file1.getParent());
            String proc = StringUtils.getProcStr(atomicLong.get(), finalTotal, 10);
            log.info("mkdir" + proc + " " + file1.getName() + " at " + path);
            mkdir(0, file1.getName(), path);
            atomicLong.incrementAndGet();
        }

        // 再添加文件
        atomicLong.set(0);
        total = dirCollection.getSize();
        long finalTotal1 = total;
        for (File file : dirCollection.getFileList()) {
            String path = PathUtils.getRelativePath(0, file.getParent());
            String proc = StringUtils.getProcStr(atomicLong.get(), finalTotal1, 10);
            log.info("addFile " + proc + " " + file.getName() + " at " + path);
            FileInfo fileInfo = new FileInfo(file);
            fileInfo.updateMd5();
            addRecord(0, file.getName(), file.length(), fileInfo.getMd5(), path);
            atomicLong.addAndGet(file.length());
        }
        log.info("Finish");
    }

    /**
     * 删除一个文件夹下的所有文件记录
     * @param uid   用户ID 0表示公共
     * @param dirInfo   文件夹信息
     * @return 删除的条数
     */
    private int deleteDirRecord(int uid, FileInfo dirInfo) {

        // 目录下的所有子目录信息
        List<NodeInfo> childNodes = nodeService.getChildNodes(uid, dirInfo.getMd5());
        List<String> ids = new LinkedList<>();
        ids.add(dirInfo.getMd5());
        childNodes.forEach(nodeInfo -> ids.add(nodeInfo.getId()));
        int res = 0;
        nodeService.deleteNodes(uid, ids);
        if (!ids.isEmpty()) {
            res += fileDao.deleteDirsRecord(uid, ids);
        }
        res += fileDao.deleteRecord(uid, dirInfo.getParent(), Collections.singletonList(dirInfo.getName()));
        return res;
    }
}
