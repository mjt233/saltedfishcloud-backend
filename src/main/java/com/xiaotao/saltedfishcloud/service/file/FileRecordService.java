package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.dao.PathMapDao;
import com.xiaotao.saltedfishcloud.po.FileInfo;
import com.xiaotao.saltedfishcloud.po.PathInfo;
import com.xiaotao.saltedfishcloud.utils.PathBuilder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
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
    private PathMapService pathMapService;

    @Resource
    private PathMapDao pathMapDao;

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
        String nid = SecureUtils.getMd5(path);
        pathMapService.setRecord(path, nid);
        return fileDao.addRecord(uid, name, size, md5, nid);
    }

    /**
     * 更新一条记录
     * @param uid   用户ID 0表示公共
     * @param name  文件名
     * @param path  文件所在路径
     * @param newSize 新的文件大小
     * @param newMd5 新的文件MD5
     * @return 影响行数
     */
    int updateRecord(int uid, String name, String path, Long newSize,String newMd5) {
        String nid = SecureUtils.getMd5(path);
        FileInfo fileInfo = fileDao.getFileInfo(uid, name, path);
        if(fileInfo != null && fileInfo.isDir()) {
            String path2;
            if ("/".equals(path)) {
                path2 = path + name;
            } else {
                path2 = path + "/" + name;
            }
            String nid2 = SecureUtils.getMd5(path2);
            pathMapService.deleteRecord(nid2);
            pathMapService.setRecord(path2, nid2);
        }
        return fileDao.updateRecord(uid, name, nid, newSize, newMd5);
    }

    /**
     * 删除记录,如果目标是文件夹，则会连将子目录一同移除
     * @param uid 用户ID 0表示公共
     * @param name 文件名
     * @param path 文件所在路径
     * @return 影响行数
     */
    public int deleteRecord(int uid, String name, String path) {
        String nid = SecureUtils.getMd5(path);
        FileInfo fileInfo = fileDao.getFileInfo(uid, name, nid);
        if (fileInfo != null && fileInfo.getSize() == -1L) {
            return deleteDirRecord(uid, name, path);
        } else {
            fileDao.deleteRecord(uid, nid, Collections.singletonList(name));
            return pathMapService.deleteRecord( SecureUtils.getMd5( path ) );
        }
    }

    /**
     * 批量删除某个目录下的文件或文件夹，文件夹的所有子文件夹和文件也会被一同删除
     * @param uid   用户ID 0表示公共
     * @param path  路径
     * @param name  文件名列表
     * @return 删除的文件个数
     */
    public int deleteRecords(int uid, String path, Collection<String> name) {
        String nid = SecureUtils.getMd5(path);
        List<FileInfo> infos = fileDao.getFilesInfo(uid, name, nid);
        LinkedList<String> dirs = new LinkedList<>(), files = new LinkedList<>();
        infos.forEach(info -> {
            if( info.isDir()) {
                dirs.push(info.getName());
            } else {
                files.push(info.getName());
            }
        });
        AtomicInteger cnt = new AtomicInteger();
        dirs.forEach(dir -> cnt.addAndGet(deleteDirRecord(uid, dir, path)));
        if (files.size() != 0) {
            cnt.addAndGet(fileDao.deleteRecord(uid, nid, files));
        }
        return cnt.get();
    }

    private int deleteDirRecord(int uid, String name, String path) {
        // 将路径和文件名合成为一个文件类型为文件夹的路径方便将整个文件夹相关的记录移除
        PathBuilder pb = new PathBuilder();
        pb.append(path).append(name);
        String nodePath = pb.toString();

        // 目录下的所有子目录信息
        List<PathInfo> pathInfos = pathMapDao.getDirTreeIds(nodePath);
        pathInfos.add(new PathInfo(nodePath, SecureUtils.getMd5(nodePath)));
        LinkedList<String> ids = new LinkedList<>();
        pathInfos.forEach(i -> ids.addLast(i.getId()));

        System.out.println(pathInfos);
        if (ids.size() > 1) {
            // 先删除文件记录表 - 子目录
            fileDao.deleteDirsRecord(uid, ids);
        }

        // 再删除自己本身
        fileDao.deleteRecord(uid, SecureUtils.getMd5(path), Collections.singletonList(name));

        // 再删除路径映射表信息
        return pathMapDao.removePathsRecord(ids);
    }
}
