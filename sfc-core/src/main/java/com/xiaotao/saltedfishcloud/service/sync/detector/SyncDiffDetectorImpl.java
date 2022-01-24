package com.xiaotao.saltedfishcloud.service.sync.detector;

import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.sync.model.FileChangeInfo;
import com.xiaotao.saltedfishcloud.service.sync.model.SyncDiffResultDefaultImpl;
import com.xiaotao.saltedfishcloud.utils.DiskFileUtils;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.SetUtils;
import lombok.var;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Deprecated
public class SyncDiffDetectorImpl implements SyncDiffDetector {
    @Resource
    private NodeService nodeService;
    @Resource
    private DiskFileSystemFactory fileService;


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
    public SyncDiffResultDefaultImpl detect(User user) throws Exception {
        int uid = user.getId();
        SyncDiffResultDefaultImpl res = new SyncDiffResultDefaultImpl();

        // 原始数据获取与初步处理

        // 数据库中所有文件信息
        Map<String, Collection<? extends FileInfo>> dbFile = fetchDbFiles(uid);
        // 用户目录本地硬盘上的信息集合
        DirCollection local = FileUtils.scanDir(Paths.get(LocalStoreConfig.getPathHandler().getStorePath(user.getId(), "/", null)));
        // 本地硬盘上的目录集合
        Set<String> localDir = local.getDirList().stream().map(e -> DiskFileUtils.getRelativePath(user, e.getPath())).collect(Collectors.toSet());
        localDir.add("/");

        //  获取本地新增目录
        LinkedList<String> newDir = new LinkedList<>(SetUtils.diff(localDir, dbFile.keySet()));
        newDir.sort(Comparator.comparingInt(o -> o.split("/").length));
        res.setNewDirPaths(newDir);

        //  获取本地已删除目录
        LinkedList<String> deletedDir = new LinkedList<>(SetUtils.diff(dbFile.keySet(), localDir));
        deletedDir.sort( Comparator.comparingInt(o -> o.split("/").length) );
        res.setDeletedDirPaths(deletedDir);

        //  数据库中的所有文件, key为文件路径，value为文件信息
        HashMap<String, FileInfo> dbFiles = new HashMap<>();
        LinkedList<FileInfo> deletedFiles = new LinkedList<>();
        dbFile.forEach((k,v) -> {
            v.forEach(fileInfo -> {
                Path path = Paths.get(LocalStoreConfig.getRawFileStoreRootPath(uid) + "/" + k + "/" + fileInfo.getName());
                if ( !Files.exists(path) || Files.isDirectory(path)) {
                    deletedFiles.add(fileInfo);
                } else {
                    dbFiles.put(k + (k.equals("/") ? "" : "/") + fileInfo.getName(), fileInfo);
                }
            });
        });
        res.setDeletedFiles(deletedFiles);


        //  筛选出本地文件被更改和增加的部分
        LinkedList<FileInfo> newFiles = new LinkedList<>();
        LinkedList<FileChangeInfo> changeFiles = new LinkedList<>();
        for (File e : local.getFileList()) {
            String path = DiskFileUtils.getRelativePath(user, e.getPath());
            FileInfo f = dbFiles.get(path);
            if ( f == null ) {
                FileInfo fi = new FileInfo(e);
                fi.setPath(DiskFileUtils.getRelativePath(user, e.getParent()));
                newFiles.add(fi);
            } else if ( e.length() != f.getSize() ) {
                FileInfo fi = new FileInfo(e);
                fi.setPath(DiskFileUtils.getRelativePath(user, e.getParent()));
                changeFiles.add(new FileChangeInfo(f, fi));
            }
        }
        res.setNewFiles(newFiles);
        res.setChangeFiles(changeFiles);
        return res;
    }
}
