package com.xiaotao.saltedfishcloud.init;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.dao.FileDao;
import com.xiaotao.saltedfishcloud.po.NodeInfo;
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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用于同步本地网盘文件的信息到数据库
 * @TODO 有效范围扩大到所有用户目录
 * @TODO 适配唯一文件存储方式的情况，路径操作改用PathHandler
 */
@Component
@Slf4j
public class FileDBSynchronizer implements ApplicationRunner {
    @Resource
    NodeService nodeService;
    @Resource
    FileService fileService;
    @Resource
    FileDao fileDao;
    @Resource
    FileRecordService fileRecordService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        while (DiskConfig.SYNC_DELAY > 0) {
            try {
                log.info("开始同步文件信息");
                long start = System.currentTimeMillis();
                doAction();
                log.info("同步完成，任务耗时：" + (System.currentTimeMillis() - start)/1000 + "s");
                Thread.sleep(DiskConfig.SYNC_DELAY*1000*60);
            } catch (Exception e) {
                log.warn("同步出错：" + e.getMessage() + " 本轮同步任务跳过，等待下一轮");
                Thread.sleep(DiskConfig.SYNC_DELAY*1000*60);
            }
        }
    }

    public void doAction() throws IOException {

        List<NodeInfo> node = nodeService.getChildNodes(0, "root");
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId("root");
        nodeInfo.setName("/");
        nodeInfo.setUid(0);
        node.add(nodeInfo);
        // 数据库中的文件结构，key为目录，value为文件列表
        Map<String, Collection<? extends FileInfo>> dbFile = new HashMap<>();
        node.forEach(n -> {
            Collection<? extends FileInfo>[] fileList = fileService.getUserFileListByNodeId(0, n.getId());
            String path = nodeService.getPathByNode(0, n.getId());
            dbFile.put(path, fileList[1]);
        });
        DirCollection local = FileUtils.scanDir(DiskConfig.PUBLIC_ROOT);
        LinkedList<File> dirList = local.getDirList();
        dirList.add(new File(DiskConfig.PUBLIC_ROOT));
        Set<String> localDir = new HashSet<>();
        dirList.forEach(e -> {
            localDir.add( PathUtils.getRelativePath(0, e.getPath()));
        });

        //  处理数据库缺失的目录
        LinkedList<String> dbLostDir = new LinkedList<>(SetUtils.diff(localDir, dbFile.keySet()));
        dbLostDir.sort(Comparator.comparingInt(o -> o.split("/").length));

        long cnt = 0, total = dbLostDir.size();
        for (String p1 : dbLostDir) {
            int index = p1.lastIndexOf('/');
            String path = p1.substring(0, index);
            String name = p1.substring(index + 1);
            fileRecordService.mkdir(0, name, path.length() == 0 ? "/" : path);
            String proc = "创建目录:" + StringUtils.getProcStr(++cnt, total, 32);
            log.info(proc);
        }

        //  处理数据库中已失效目录
        LinkedList<String> localLostDir = new LinkedList<>(SetUtils.diff(dbFile.keySet(), localDir));
        Set<String> hasDelete = new HashSet<>();
        localLostDir.sort( Comparator.comparingInt(o -> o.split("/").length) );
        localLostDir.forEach(p -> {
            int index = p.lastIndexOf('/');
            String path = p.substring(0, index);
            String name = p.substring(index + 1);
            String[] node1 = PathUtils.getAllNode(path);
            for (String s : node1) {
                if (hasDelete.contains(s)) {
                    return;
                }
            }
            fileRecordService.deleteRecords(0, path, Collections.singleton(name));
            hasDelete.add(p);
        });

        //  数据库中的所有文件, key为文件路径，value为文件信息
        HashMap<String, FileInfo> dbFiles = new HashMap<>();
        AtomicLong deleteCnt = new AtomicLong();
        dbFile.forEach((k,v) -> {
            v.forEach(fileInfo -> {
                Path path = Paths.get(DiskConfig.getRawFileStoreRootPath(0) + "/" + k + "/" + fileInfo.getName());
                if ( !Files.exists(path)) {
                    deleteCnt.getAndIncrement();
                    fileDao.deleteRecord(0, fileInfo.getNode(), Collections.singletonList(fileInfo.getName()));
                } else {
                    dbFiles.put(k + (k.equals("/") ? "" : "/") + fileInfo.getName(), fileInfo);
                }
            });
        });

        //  筛选出本地文件被更改和增加的部分
        LinkedList<FileInfo> newFile = new LinkedList<>(), changeFile = new LinkedList<>();
        for (File e : local.getFileList()) {
            String path = PathUtils.getRelativePath(0, e.getPath());
            FileInfo f = dbFiles.get(path);
            if ( f == null ) {
                FileInfo fi = new FileInfo(e);
                fi.setPath(PathUtils.getRelativePath(0, e.getParent()));
                newFile.add(fi);
            } else if ( e.length() != f.getSize() ) {
                FileInfo fi = new FileInfo(e);
                fi.setPath(PathUtils.getRelativePath(0, e.getParent()));
                changeFile.add(fi);
            }
        }

        cnt = 0;
        total = newFile.size();
        for (FileInfo fileInfo : newFile) {
            fileInfo.updateMd5();
            String proc = "添加文件:" + StringUtils.getProcStr(++cnt, total, 32);
            if (fileRecordService.addRecord(0, fileInfo.getName(), fileInfo.getSize(), fileInfo.getMd5(), fileInfo.getPath()) <= 0) {
                log.error("信息添加失败：" + fileInfo.getPath() + "/" + fileInfo.getName() + " MD5:" + fileInfo.getMd5());
            }
            log.info(proc);
        }

        cnt = 0;
        total = changeFile.size();
        for (FileInfo fileInfo : changeFile) {
            fileInfo.updateMd5();
            fileDao.updateRecord(
                    0,
                    fileInfo.getName(),
                    nodeService.getNodeIdByPath(0, fileInfo.getPath()).getId(),
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
    }
}
