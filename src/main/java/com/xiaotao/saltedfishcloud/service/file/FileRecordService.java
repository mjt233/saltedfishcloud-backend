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
        String nodeId = nodeService.getNodeIdByPath(path);
        return fileDao.addRecord(uid, name, size, md5, nodeId);
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
        String nid = nodeService.getNodeIdByPath(path);
        return fileDao.updateRecord(uid, name, nid, newSize, newMd5);
    }

    /**
     * 批量删除某个目录下的文件或文件夹，文件夹的所有子文件夹和文件也会被一同删除
     * @param uid   用户ID 0表示公共
     * @param path  路径
     * @param name  文件名列表
     * @return 删除的文件个数
     */
    public int deleteRecords(int uid, String path, Collection<String> name) {
        String nid = nodeService.getNodeIdByPath(path);
        List<FileInfo> infos = fileDao.getFilesInfo(uid, name, nid);

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

        dirs.forEach(dir -> cnt.addAndGet(deleteDirRecord(uid, dir, nid)));
        if (files.size() != 0) {
            cnt.addAndGet(fileDao.deleteRecord(uid, nid, files));
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
        String pid = nodeService.getNodeIdByPath(path);
        nodeService.addNode(uid, name, pid);
        fileDao.addRecord(uid, name, (long) -1, null, pid);
    }

    /**
     * 删除一个文件夹下的所有文件记录
     * @param uid   用户ID 0表示公共
     * @param name  文件夹名
     * @param nid  文件夹所在节点
     * @TODO 增加删除无效的节点记录
     * @return 删除的条数
     */
    private int deleteDirRecord(int uid, String name, String nid) {

        // 目录下的所有子目录信息
        List<NodeInfo> childNodes = nodeService.getChildNodes(uid, nid);
        List<String> ids = new LinkedList<>();
        childNodes.forEach(nodeInfo -> {
            ids.add(nodeInfo.getId());
        });
        int res = 0;
        nodeDao.deleteNodes(uid, ids);
        res += fileDao.deleteDirsRecord(uid, ids);
        res += fileDao.deleteRecord(uid, nid, Collections.singletonList(name));
        return res;
    }
}
