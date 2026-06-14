package com.sfc.dm.task.detect.scanner;

import com.sfc.dm.model.po.InvalidDataRecord;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.service.node.FileTree;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * RAW 模式失效数据扫描器
 */
@Slf4j
public class RawModeScanner extends AbstractInvalidDataScanner {
    @Setter
    private DiskFileSystemManager fileSystemManager;
    @Setter
    private FileRecordService fileRecordService;

    @Override
    public List<InvalidDataRecord> scan() {
        log("开始RAW模式扫描...");
        List<InvalidDataRecord> results = new ArrayList<>();
        Storage storage = storeServiceFactory.getService().getStorageProvider();

        // 1. 扫描失效物理存储（物理文件存在但文件记录不存在）
        log("扫描失效物理存储...");
        scanInvalidPhysicalStorage(storage, results);

        // 2. 扫描失效文件记录（文件记录存在但物理文件不存在）
        log("扫描失效文件记录...");
        scanInvalidFileRecords(results);

        log("RAW模式扫描完成，发现 " + results.size() + " 条失效数据");
        return results;
    }

    /**
     * 扫描失效物理存储（物理文件存在但文件记录不存在）
     */
    private void scanInvalidPhysicalStorage(Storage storage, List<InvalidDataRecord> results) {
        // 扫描公共网盘
        String publicRoot = sysProperties.getStore().getPublicRoot();
        log("扫描公共网盘: " + publicRoot);
        try {
            scanDirectory(storage, publicRoot, "/", 0L, results);
        } catch (IOException e) {
            log("扫描公共网盘失败: " + e.getMessage());
        }

        // 扫描用户网盘
        String storeRoot = sysProperties.getStore().getRoot();
        String userFileRoot = StringUtils.appendPath(storeRoot + "/user_file");
        forEachUser(user -> {
            String userDir = StringUtils.appendPath(userFileRoot, user.getId().toString());
            log("扫描用户网盘: uid=" + user.getId());
            try {
                scanDirectory(storage, userDir, "/", user.getId(), results);
            } catch (IOException e) {
                log("扫描用户网盘失败 [" + userDir + "]: " + e.getMessage());
            }
        });
    }

    /**
     * 扫描失效文件记录（文件记录存在但物理文件不存在）
     */
    private void scanInvalidFileRecords(List<InvalidDataRecord> results) {
        DiskFileSystem fs = fileSystemManager.getMainFileSystem();

        // 扫描公共网盘的文件记录
        log("扫描公共网盘文件记录...");
        scanFileRecordsByUid(0L, results);

        // 扫描用户网盘的文件记录
        forEachUser(user -> {
            log("扫描用户文件记录: uid=" + user.getId());
            scanFileRecordsByUid(user.getId(), results);
        });
    }

    private String getUserStoragePath(Long uid) {
        return uid == UserConstants.PUBLIC_USER_ID ? sysProperties.getStore().getPublicRoot() : StringUtils.appendPath(sysProperties.getStore().getRoot(), "user_file", uid.toString());
    }

    /**
     * 扫描指定用户的文件记录，检查物理文件是否存在
     *
     * @param uid    用户ID
     * @param results 接收的结果列表
     */
    private void scanFileRecordsByUid(Long uid, List<InvalidDataRecord> results) {
        FileTree fileTree = fileRecordService.getFullTree(uid);
        Storage storage = storeServiceFactory.getService().getStorageProvider();
        String storageRoot = getUserStoragePath(uid);
        String rootMd5 = uid.toString();
        for (FileInfo node : fileTree) {
            if (interrupted.get()) {
                break;
            }
            // 跳过挂载文件
            if (Boolean.TRUE.equals(node.getIsMount()) || node.getMountId() != null) {
                continue;
            }
            String diskParentPath = rootMd5.equals(node.getMd5()) ? "/" : StringUtils.appendPath(fileTree.getPath(node.getNode()), node.getName());
            List<FileInfo> files = fileRecordService.findByUidAndNodeId(uid, node.getMd5());

            for (FileInfo file : files) {
                if (interrupted.get()) {
                    break;
                }
                // 跳过挂载文件
                if (Boolean.TRUE.equals(file.getIsMount()) || file.getMountId() != null) {
                    continue;
                }
                String diskPath = StringUtils.appendPath(diskParentPath, file.getName());
                String storagePath = StringUtils.appendPath(storageRoot, diskParentPath, file.getName());
                try {
                    // 检查物理文件是否存在
                    if (!storage.exist(storagePath)) {
                        // 文件记录存在但物理文件不存在 -> 失效文件记录
                        InvalidDataRecord invalidRecord = createInvalidFileRecordRecord(
                                storagePath, uid, diskPath, file.getSize(), file.getUpdateAt(), file.getMd5());
                        results.add(invalidRecord);
                    }
                } catch (IOException e) {
                    log("检查物理文件失败 [uid=" + uid + ", path=" + diskPath + "]: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 扫描目录下的文件，检查文件记录是否存在
     *
     * @param storage   物理存储
     * @param path      物理存储的扫描路径
     * @param diskRoot  对应的DiskFileSystem根路径，用于计算磁盘相对路径
     * @param ownerUid  查询文件记录的文件所属用户id（公共网盘为0）
     * @param results   接收的结果列表
     */
    private void scanDirectory(Storage storage, String path, String diskRoot, Long ownerUid, List<InvalidDataRecord> results) throws IOException {
        if (interrupted.get()) {
            return;
        }
        List<FileInfo> files = storage.listFiles(path);
        if (files == null) {
            return;
        }

        DiskFileSystem fs = fileSystemManager.getMainFileSystem();
        for (FileInfo file : files) {
            if (interrupted.get()) {
                return;
            }
            String fullPath = StringUtils.appendPath(path, file.getName());
            String diskPath = StringUtils.appendPath(diskRoot, file.getName());
            if (file.isDir()) {
                scanDirectory(storage, fullPath, diskPath, ownerUid, results);
            } else {
                // 检查文件记录是否存在
                try {
                    if (!fs.exist(ownerUid, diskPath)) {
                        // 物理存储存在但文件记录不存在 -> 失效物理存储
                        InvalidDataRecord invalidRecord = createInvalidPhysicalStorageRecord(
                                fullPath, ownerUid, diskPath, file.getSize(), new Date(file.getMtime()), file.getMd5());
                        results.add(invalidRecord);
                    }
                } catch (IOException e) {
                    log("检查文件记录失败 [" + fullPath + "]: " + e.getMessage());
                }
            }
        }
    }
}
