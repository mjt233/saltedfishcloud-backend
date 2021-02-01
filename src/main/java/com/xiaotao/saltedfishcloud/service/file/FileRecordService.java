package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.dao.NodeDao;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 文件索引记录服务，管理数据库中的文件结构表
 */
@Service
public class FileRecordService {
    @Resource
    private FileDao fileDao;

    @Resource
    private NodeService nodeService;

    @Resource
    private NodeDao nodeDao;

    /**
     * 添加一个记录
     * @param uid   用户ID 0表示公共
     * @param name  文件名
     * @param size  文件大小
     * @param md5   文件MD5
     * @param path  文件所在路径
     * @return 添加数量
     */
    public int addRecord(int uid, String name, Long size, String md5, String path) {
        NodeInfo node = nodeService.getNodeIdByPath(uid, path);
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
    int updateFileRecord(int uid, String name, String path, Long newSize, String newMd5) {
        NodeInfo node = nodeService.getNodeIdByPath(uid, path);
        return fileDao.updateRecord(uid, name, node.getId(), newSize, newMd5);
    }

    /**
     * 批量删除某个目录下的文件或文件夹，文件夹的所有子文件夹和文件也会被一同删除
     * @param uid   用户ID 0表示公共
     * @param path  路径
     * @param name  文件名列表
     * @return 删除的文件个数
     */
    public int deleteRecords(int uid, String path, Collection<String> name) {
        NodeInfo node = nodeService.getNodeIdByPath(uid, path);
        List<FileInfo> infos = fileDao.getFilesInfo(uid, name, node.getId());

        // 先将文件和文件夹分开并单独提取其文件名
        LinkedList<String> dirs = new LinkedList<>(), files = new LinkedList<>();
        infos.forEach(info -> {
            if( info.isDir()) {
                dirs.push(info.getName());
            } else {
                files.push(info.getName());
            }
        });
        AtomicInteger cnt = new AtomicInteger();

        dirs.forEach(dir -> cnt.addAndGet(deleteDirRecord(uid, dir, node.getId())));
        if (files.size() != 0) {
            cnt.addAndGet(fileDao.deleteRecord(uid, node.getId(), files));
        }
        return cnt.get();
    }

    /**
     * 向数据库系统新建一个文件夹记录
     * @param uid   用户ID
     * @param name  文件夹名称
     * @param path  路径
     */
    public void mkdir(int uid, String name, String path) {
        NodeInfo node = nodeService.getNodeIdByPath(uid, path);
        nodeService.addNode(uid, name, node.getId());
        fileDao.addRecord(uid, name, (long) -1, null, node.getId());
    }

    /**
     * 对文件或文件夹进行重命名
     * @param uid   用户ID
     * @param path  目标文件或文件夹所在路径
     * @param oldName  旧文件名
     * @param newName 新文件名
     */
    public void rename(int uid, String path, String oldName, String newName) {
        NodeInfo pathNodeInfo = nodeService.getNodeIdByPath(uid, path);
        NodeInfo nodeInfo = nodeService.getNodeIdByPath(uid, path + "/" + oldName);
        FileInfo fileInfo = fileDao.getFileInfo(uid, oldName, pathNodeInfo.getId());
        if (fileInfo.isDir()) {
            nodeDao.changeName(uid, nodeInfo.getId(), newName);
        }
        fileDao.rename(uid, pathNodeInfo.getId(), oldName, newName);
    }

    /**
     * 删除一个文件夹下的所有文件记录
     * @param uid   用户ID 0表示公共
     * @param name  文件夹名
     * @param nid  文件夹所在节点
     * @return 删除的条数
     */
    private int deleteDirRecord(int uid, String name, String nid) {

        // 目录下的所有子目录信息
        List<NodeInfo> childNodes = nodeService.getChildNodes(uid, nid);
        List<String> ids = new LinkedList<>();
        childNodes.forEach(nodeInfo -> ids.add(nodeInfo.getId()));
        int res = 0;
        nodeService.deleteNodes(uid, ids);
        res += fileDao.deleteDirsRecord(uid, ids);
        res += fileDao.deleteRecord(uid, nid, Collections.singletonList(name));
        return res;
    }
}
