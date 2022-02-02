package com.xiaotao.saltedfishcloud.service.sync.detector;

import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.sync.model.FileChangeInfo;
import com.xiaotao.saltedfishcloud.service.sync.model.SyncDiffResultDefaultImpl;
import com.xiaotao.saltedfishcloud.utils.*;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SyncDiffDetectorImpl implements SyncDiffDetector {
    @Resource
    private NodeService nodeService;
    @Resource
    private DiskFileSystemFactory fileService;
    @Resource
    private StoreServiceFactory storeServiceFactory;

    /**
     * 通过存储服务获取用户的完整目录和文件
     * @param uid           待查找的用户网盘存储数据的用户ID，公共网盘为0
     * @param storeService  存储服务
     * @return key - 文件完整路径，value - 文件信息
     */
    protected Map<String, FileInfo> getStoreAllFile(int uid, StoreService storeService) throws IOException {
        log.debug("[FileRecordSync]获取uid {} 完整的目录和文件", uid);
        // 利用栈使用循环迭代方式实现的深度优先搜索（DFS）遍历

        //  结果HashMap
        Map<String, FileInfo> res = new HashMap<>();

        //  待搜索队列/栈，默认从根目录开始搜索
        LinkedList<String> searchQueue = new LinkedList<>();
        searchQueue.add("/");

        while (!searchQueue.isEmpty()) {
            String curDir = searchQueue.pop();
            final List<FileInfo> files = storeService.lists(uid, curDir);
            for (FileInfo file : files) {
                String fileFullPath = StringUtils.appendPath(curDir, file.getName());
                file.setPath(PathUtils.getParentPath(fileFullPath));
                if (file.isDir()) {
                    searchQueue.push(fileFullPath);
                }
                res.put(fileFullPath, file);
            }
        }
        return res;
    }

    /**
     * 从数据库抓取用户指定节点下的文件信息
     * @return 以目录为Key，目录下的文件列表为Value的Map集合
     */
    private Map<String, Collection<? extends FileInfo>> fetchDbFiles(int uid) {
        Map<String, Collection<? extends FileInfo>> dbFile = new HashMap<>();
        var tree = nodeService.getFullTree(uid);
        DiskFileSystem fileSystem = fileService.getFileSystem();
        tree.forEach(n -> {
            Collection<? extends FileInfo>[] fileList = fileSystem.getUserFileListByNodeId(uid, n.getId());
            String path = tree.getPath(n.getId());
            dbFile.put(path, fileList[1]);
        });
        return dbFile;
    }

    @Override
    public SyncDiffResultDefaultImpl detect(int uid, boolean precise) throws IOException {
        final StoreService storeService = storeServiceFactory.getService();
        SyncDiffResultDefaultImpl res = new SyncDiffResultDefaultImpl();

        // 原始数据获取与初步处理

        //  数据库中所有文件信息
        Map<String, Collection<? extends FileInfo>> recordAllFile = fetchDbFiles(uid);

        //  存储服务中的所有文件信息
        final Map<String, FileInfo> storeAllFile = this.getStoreAllFile(uid, storeService);

        //  存储服务上的目录集合
        Set<String> storeDir = storeAllFile.entrySet().stream()
                .filter(e -> e.getValue().isDir())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        storeDir.add("/");

        //  获取存储服务新增目录
        LinkedList<String> newDir = new LinkedList<>(SetUtils.diff(storeDir, recordAllFile.keySet()));
        newDir.sort(Comparator.comparingInt(o -> o.split("/").length));
        res.setNewDirPaths(newDir);

        //  获取记录服务中，实际上存储服务已删除目录
        LinkedList<String> deletedDir = new LinkedList<>(SetUtils.diff(recordAllFile.keySet(), storeDir));
        deletedDir.sort( Comparator.comparingInt(o -> o.split("/").length) );
        res.setDeletedDirPaths(deletedDir);

        //  获取记录服务中，实际上存储服务已删除的文件
        //  数据库中的所有文件（不含目录）, key为文件在网盘中的完整路径，value为文件信息
        HashMap<String, FileInfo> recordFiles = new HashMap<>();
        LinkedList<FileInfo> deletedFiles = new LinkedList<>();
        recordAllFile.forEach((k,v) -> {
            v.forEach(fileInfo -> {
                String path = StringUtils.appendPath(k, fileInfo.getName());
                final FileInfo storeFileInfo = storeAllFile.get(path);
                if (storeFileInfo == null || storeFileInfo.isDir()) {
                    deletedFiles.add(fileInfo);
                } else {
                    recordFiles.put(k + (k.equals("/") ? "" : "/") + fileInfo.getName(), fileInfo);
                }
            });
        });
        res.setDeletedFiles(deletedFiles);


        //  筛选出本地文件被更改和增加的部分
        LinkedList<FileInfo> newFiles = new LinkedList<>();
        LinkedList<FileChangeInfo> changeFiles = new LinkedList<>();
        storeAllFile.forEach((k, v) -> {
            try {
                final FileInfo recordInfo = recordFiles.get(k);
                if (recordInfo == null) {
                    newFiles.add(v);
                } else if (recordInfo.getSize() != v.getSize()) {
                    changeFiles.add(new FileChangeInfo(recordInfo, v));
                } else if (precise) {

                    // 精确同步模式下需计算文件MD5
                    if (v.getMd5() == null) {
                        v.updateMd5();
                    }
                    if (!recordInfo.getMd5().equals(v.getMd5())) {
                        changeFiles.add(new FileChangeInfo(recordInfo, v));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        res.setNewFiles(newFiles);
        res.setChangeFiles(changeFiles);
        return res;
    }
}
