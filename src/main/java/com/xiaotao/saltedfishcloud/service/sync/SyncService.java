package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SetUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class SyncService {
    private final static ReadOnlyLevel readOnlyLevel = ReadOnlyLevel.DATA_CHECKING;

    @Resource
    private NodeService nodeService;
    @Resource
    private FileService fileService;
    private final SyncDiffHandler handler;

    SyncService(SyncDiffHandler handler) {
        this.handler = handler;
    }

    /**
     * 同步目标用户的数据库与本地文件信息
     * @TODO 性能优化：找出被重命名的文件，目录和被移动的目录
     * @param user  用户对象信息
     * @throws IOException IO出错
     */
    public void syncLocal(User user) throws Exception {
        int uid = user.getId();
        try {
            DiskConfig.setReadOnlyLevel(readOnlyLevel);
            List<NodeInfo> node = nodeService.getChildNodes(user.getId(), "root");
            NodeInfo nodeInfo = new NodeInfo();
            nodeInfo.setId("root");
            nodeInfo.setName("/");
            nodeInfo.setUid(user.getId());
            node.add(nodeInfo);

            // 数据库中的文件结构，key为目录，value为文件列表
            Map<String, Collection<? extends FileInfo>> dbFile = new HashMap<>();
            node.forEach(n -> {
                Collection<? extends FileInfo>[] fileList = fileService.getUserFileListByNodeId(uid, n.getId());
                String path = nodeService.getPathByNode(uid, n.getId());
                dbFile.put(path, fileList[1]);
            });
            DirCollection local = FileUtils.scanDir(Paths.get(DiskConfig.getPathHandler().getStorePath(uid, "/", null)));
            LinkedList<File> dirList = local.getDirList();
            dirList.add(new File(user.isPublicUser() ? DiskConfig.PUBLIC_ROOT : DiskConfig.getUserPrivateDiskRoot(user.getUser())));
            Set<String> localDir = dirList.stream()
                    .map(e -> PathUtils.getRelativePath(user, e.getPath()))
                    .collect(Collectors.toSet());

            //  处理数据库缺失的目录
            LinkedList<String> newDir = new LinkedList<>(SetUtils.diff(localDir, dbFile.keySet()));
            newDir.sort(Comparator.comparingInt(o -> o.split("/").length));

            //  处理数据库中已失效目录
            LinkedList<String> deletedDir = new LinkedList<>(SetUtils.diff(dbFile.keySet(), localDir));
            deletedDir.sort( Comparator.comparingInt(o -> o.split("/").length) );

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


            handler.handleDirDel(user, deletedDir);
            handler.handleFileDel(user, deletedFiles);
            handler.handleDirAdd(user, newDir);
            handler.handleFileAdd(user, newFiles);
            handler.handleFileChange(user, changeFiles);
            log.info("==== 任务统计 ====");
            log.info("被删除的目录数：" + deletedDir.size());
            log.info("新增的目录数：" + newDir.size());
            log.info("新增的文件数：" + newFiles.size());
            log.info("被更改的文件数：" + changeFiles.size());
            log.info("被删除的文件数：" + deletedFiles.size());
            log.info("==== 任务完成 ====");

            DiskConfig.setReadOnlyLevel(null);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            DiskConfig.setReadOnlyLevel(null);
            throw e;
        }
    }
}
