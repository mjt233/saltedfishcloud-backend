package com.xiaotao.saltedfishcloud.service.sync.detector;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.service.sync.model.FileChangeInfo;
import com.xiaotao.saltedfishcloud.service.sync.model.SyncDiffResultDefaultImpl;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SetUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SyncDiffDetectorImpl implements SyncDiffDetector {
    @Resource
    private NodeService nodeService;
    @Resource
    private FileService fileService;


    /**
     * 从数据库抓取用户指定节点下的文件信息
     * @return 以目录为Key，目录下的文件列表为Value的Map集合
     */
    private Map<String, Collection<? extends FileInfo>> fetchDbFiles(int uid, List<NodeInfo> nodes) {
        Map<String, Collection<? extends FileInfo>> dbFile = new HashMap<>();
        nodes.forEach(n -> {
            Collection<? extends FileInfo>[] fileList = fileService.getUserFileListByNodeId(uid, n.getId());
            String path = nodeService.getPathByNode(uid, n.getId());
            dbFile.put(path, fileList[1]);
        });
        return dbFile;
    }

    /**
     * 从数据库抓取用户的所有目录节点信息
     * @TODO 性能优化：已获取所有的节点信息，使用已获取的信息直接查询节点路径，避免重复操作数据库
     */
    private List<NodeInfo> fetchNodes(int uid) {
        // 获取根目录下的所有子目录
        List<NodeInfo> nodes = nodeService.getChildNodes(uid, "root");

        // 所有目录的集合
        NodeInfo nodeInfo = new NodeInfo();
        //   目录集合里加上根目录本身的信息
        nodeInfo.setId("root");
        nodeInfo.setName("/");
        nodeInfo.setUid(uid);
        nodes.add(nodeInfo);
        return nodes;
    }

    @Override
    public SyncDiffResultDefaultImpl detect(User user) throws Exception {
        int uid = user.getId();
        SyncDiffResultDefaultImpl res = new SyncDiffResultDefaultImpl();

        // 原始数据获取与初步处理

        // 所有目录节点
        List<NodeInfo> nodes = fetchNodes(uid);
        // 数据库中所有文件信息
        Map<String, Collection<? extends FileInfo>> dbFile = fetchDbFiles(uid, nodes);
        // 用户目录本地硬盘上的信息集合
        DirCollection local = FileUtils.scanDir(Paths.get(DiskConfig.getPathHandler().getStorePath(user.getId(), "/", null)));
        // 本地硬盘上的目录集合
        Set<String> localDir = local.getDirList().stream().map(e -> PathUtils.getRelativePath(user, e.getPath())).collect(Collectors.toSet());
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
                Path path = Paths.get(DiskConfig.getRawFileStoreRootPath(uid) + "/" + k + "/" + fileInfo.getName());
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
            String path = PathUtils.getRelativePath(user, e.getPath());
            FileInfo f = dbFiles.get(path);
            if ( f == null ) {
                FileInfo fi = new FileInfo(e);
                fi.setPath(PathUtils.getRelativePath(user, e.getParent()));
                newFiles.add(fi);
            } else if ( e.length() != f.getSize() ) {
                FileInfo fi = new FileInfo(e);
                fi.setPath(PathUtils.getRelativePath(user, e.getParent()));
                changeFiles.add(new FileChangeInfo(f, fi));
            }
        }
        res.setNewFiles(newFiles);
        res.setChangeFiles(changeFiles);
        return res;
    }
}
