package com.xiaotao.saltedfishcloud.service.sync;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.po.file.DirCollection;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SetUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
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
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class SyncService {
    private final static ReadOnlyLevel readOnlyLevel = ReadOnlyLevel.DATA_CHECKING;

    @Resource
    private NodeService nodeService;
    @Resource
    private FileRecordService fileRecordService;
    @Resource
    private FileService fileService;
    @Resource
    private FileDao fileDao;

    /**
     * 同步目标用户的数据库与本地文件信息
     * @param user  用户对象信息
     * @throws IOException IO出错
     */
    public void syncLocal(User user) throws IOException {
        if (!user.isPublicUser() && DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
            throw new IllegalStateException("无法在UNIQUE存储模式下对非公共网盘进行数据同步");
        }
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
            Set<String> localDir = new HashSet<>();
            dirList.forEach(e -> {
                localDir.add( PathUtils.getRelativePath(user, e.getPath()));
            });

            //  处理数据库缺失的目录
            LinkedList<String> dbLostDir = new LinkedList<>(SetUtils.diff(localDir, dbFile.keySet()));
            dbLostDir.sort(Comparator.comparingInt(o -> o.split("/").length));

            long cnt = 0, total = dbLostDir.size();
            for (String p1 : dbLostDir) {
                int index = p1.lastIndexOf('/');
                String path = p1.substring(0, index);
                String name = p1.substring(index + 1);
                fileRecordService.mkdir(uid, name, path.length() == 0 ? "/" : path);
                String proc = "创建目录:" + StringUtils.getProcStr(++cnt, total, 32);
                log.info(proc);
            }

            //  处理数据库中已失效目录
            LinkedList<String> localLostDir = new LinkedList<>(SetUtils.diff(dbFile.keySet(), localDir));
            Set<String> hasDelete = new HashSet<>();
            localLostDir.sort( Comparator.comparingInt(o -> o.split("/").length) );
            for(String p : localLostDir ){
                boolean breakFlag = false;
                int index = p.lastIndexOf('/');
                String path = p.substring(0, index);
                String name = p.substring(index + 1);
                String[] node1 = PathUtils.getAllNode(path);
                for (String s : node1) {
                    if (hasDelete.contains(s)) {
                        breakFlag = true;
                        break;
                    }
                }
                if (breakFlag) {
                    continue;
                }
                fileRecordService.deleteRecords(uid, path, Collections.singleton(name));
                hasDelete.add(p);
            }

            //  数据库中的所有文件, key为文件路径，value为文件信息
            HashMap<String, FileInfo> dbFiles = new HashMap<>();
            AtomicLong deleteCnt = new AtomicLong();
            dbFile.forEach((k,v) -> {
                v.forEach(fileInfo -> {
                    Path path = Paths.get(DiskConfig.getRawFileStoreRootPath(uid) + "/" + k + "/" + fileInfo.getName());
                    if ( !Files.exists(path)) {
                        deleteCnt.getAndIncrement();
                        fileDao.deleteRecord(uid, fileInfo.getNode(), Collections.singletonList(fileInfo.getName()));
                    } else {
                        dbFiles.put(k + (k.equals("/") ? "" : "/") + fileInfo.getName(), fileInfo);
                    }
                });
            });

            //  筛选出本地文件被更改和增加的部分
            LinkedList<FileInfo> newFile = new LinkedList<>(), changeFile = new LinkedList<>();
            for (File e : local.getFileList()) {
                String path = PathUtils.getRelativePath(user, e.getPath());
                FileInfo f = dbFiles.get(path);
                if ( f == null ) {
                    FileInfo fi = new FileInfo(e);
                    fi.setPath(PathUtils.getRelativePath(user, e.getParent()));
                    newFile.add(fi);
                } else if ( e.length() != f.getSize() ) {
                    FileInfo fi = new FileInfo(e);
                    fi.setPath(PathUtils.getRelativePath(user, e.getParent()));
                    changeFile.add(fi);
                }
            }

            cnt = 0;
            total = newFile.size();
            for (FileInfo fileInfo : newFile) {
                fileInfo.updateMd5();
                String proc = "添加文件:" + StringUtils.getProcStr(++cnt, total, 32);
                if (fileRecordService.addRecord(uid, fileInfo.getName(), fileInfo.getSize(), fileInfo.getMd5(), fileInfo.getPath()) <= 0) {
                    log.error("信息添加失败：" + fileInfo.getPath() + "/" + fileInfo.getName() + " MD5:" + fileInfo.getMd5());
                }
                log.info(proc);
            }

            cnt = 0;
            total = changeFile.size();
            for (FileInfo fileInfo : changeFile) {
                fileInfo.updateMd5();
                fileDao.updateRecord(
                        uid,
                        fileInfo.getName(),
                        nodeService.getLastNodeInfoByPath(uid, fileInfo.getPath()).getId(),
                        fileInfo.getSize(),
                        fileInfo.getMd5()
                );
                String proc = "更新文件:" + StringUtils.getProcStr(++cnt, total, 32);
                log.info(proc);
            }
            log.info("==== 任务统计 ====");
            log.info("被删除的目录数：" + localLostDir.size());
            log.info("新增的目录数：" + dbLostDir.size());
            log.info("新增的文件数：" + newFile.size());
            log.info("被更改的文件数：" + changeFile.size());
            log.info("被删除的文件数：" + deleteCnt.get());
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
