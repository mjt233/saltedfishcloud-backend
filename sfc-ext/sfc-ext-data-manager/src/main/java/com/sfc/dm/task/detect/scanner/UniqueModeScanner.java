package com.sfc.dm.task.detect.scanner;

import com.sfc.dm.model.po.InvalidDataRecord;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.service.node.FileTree;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

/**
 * UNIQUE 模式失效数据扫描器
 */
@Slf4j
public class UniqueModeScanner extends AbstractInvalidDataScanner {
    @Setter
    private FileRecordService fileRecordService;

    @Override
    public List<InvalidDataRecord> scan() {
        log("开始UNIQUE模式扫描...");
        List<InvalidDataRecord> results = new ArrayList<>();
        Storage storage = storeServiceFactory.getService().getStorageProvider();
        String storeRoot = StringUtils.appendPath(sysProperties.getStore().getRoot(), "repo");

        // 1. 扫描失效物理存储（物理文件存在但MD5没有对应文件记录）
        log("扫描失效物理存储...");
        Set<String> validMd5Set = getAllValidMd5Set();
        log("有效MD5数量: " + validMd5Set.size());
        try {
            scanUniqueStorage(storage, storeRoot, validMd5Set, results);
        } catch (IOException e) {
            log("扫描物理存储目录失败: " + e.getMessage());
        }

        // 2. 扫描失效文件记录（文件记录存在但对应的MD5物理文件不存在）
        log("扫描失效文件记录...");
        scanInvalidFileRecords(storage, storeRoot, results);

        log("UNIQUE模式扫描完成，发现 " + results.size() + " 条失效数据");
        return results;
    }

    /**
     * 扫描失效文件记录（文件记录存在但对应的MD5物理文件不存在）
     *
     * @param storage   物理存储
     * @param storeRoot 存储根目录
     * @param results   接收的结果列表
     */
    private void scanInvalidFileRecords(Storage storage, String storeRoot, List<InvalidDataRecord> results) {
        String repoRoot = StringUtils.appendPath(storeRoot, "repo");

        // 扫描公共网盘的文件记录
        log("扫描公共网盘文件记录...");
        scanFileRecordsByUid(0L, storage, repoRoot, results);

        // 扫描用户网盘的文件记录
        forEachUser(user -> {
            log("扫描用户文件记录: uid=" + user.getId());
            scanFileRecordsByUid(user.getId(), storage, repoRoot, results);
        });
    }

    /**
     * 扫描指定用户的文件记录，检查MD5对应的物理文件是否存在
     *
     * @param uid      用户ID
     * @param storage  物理存储
     * @param repoRoot 仓库根目录
     * @param results  接收的结果列表
     */
    private void scanFileRecordsByUid(Long uid, Storage storage, String repoRoot, List<InvalidDataRecord> results) {
        FileTree fileTree = fileRecordService.getFullTree(uid);
        for (FileInfo node : fileTree) {
            if (interrupted.get()) {
                break;
            }
            String parentPath = node.getPath();
            List<FileInfo> files = fileRecordService.findByUidAndNodeId(uid, node.getMd5());
            for (FileInfo file : files) {
                if (interrupted.get()) {
                    break;
                }
                // 跳过目录和挂载文件
                if (file.isDir() || Boolean.TRUE.equals(file.getIsMount())) {
                    continue;
                }
                // 跳过没有MD5的文件
                if (file.getMd5() == null) {
                    continue;
                }

                // 检查MD5对应的物理文件是否存在
                String md5Path = getMd5ResourcePath(repoRoot, file.getMd5());
                try {
                    if (!storage.exist(md5Path)) {
                        // 文件记录存在但物理文件不存在 -> 失效文件记录
                        String diskPath = StringUtils.appendPath(parentPath, file.getName());
                        InvalidDataRecord invalidRecord = createInvalidFileRecordRecord(
                                md5Path, uid, diskPath, file.getSize(), file.getUpdateAt(), file.getMd5());
                        results.add(invalidRecord);
                    }
                } catch (IOException e) {
                    log("检查物理文件失败 [uid=" + uid + ", md5=" + file.getMd5() + "]: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 获取MD5对应的物理资源路径
     *
     * @param repoRoot 仓库根目录
     * @param md5      文件MD5
     * @return 物理资源路径
     */
    private String getMd5ResourcePath(String repoRoot, String md5) {
        return StringUtils.appendPath(repoRoot, md5.substring(0, 2), md5.substring(2, 4), md5);
    }

    /**
     * 扫描物理存储文件，检查MD5是否有对应文件记录
     *
     * @param storage     物理存储
     * @param path        扫描路径
     * @param validMd5Set 有效MD5集合
     * @param results     接收的结果列表
     */
    private void scanUniqueStorage(Storage storage, String path, Set<String> validMd5Set, List<InvalidDataRecord> results) throws IOException {
        if (interrupted.get()) {
            return;
        }
        List<FileInfo> files = storage.listFiles(path);
        if (files == null) {
            return;
        }

        for (FileInfo file : files) {
            if (interrupted.get()) {
                return;
            }
            String fullPath = path + "/" + file.getName();
            if (file.isDir()) {
                scanUniqueStorage(storage, fullPath, validMd5Set, results);
            } else {
                // 无拓展名的文件即为物理存储文件
                String name = file.getName();
                if (!name.contains(".")) {
                    // 文件名即为MD5
                    if (!validMd5Set.contains(name)) {
                        // MD5没有对应文件记录 -> 失效物理存储
                        InvalidDataRecord invalidRecord = createInvalidPhysicalStorageRecord(
                                fullPath, 0L, null, file.getSize(), file.getUpdateAt(), name);
                        invalidRecord.setNeedIdentify(true);
                        results.add(invalidRecord);
                    }
                }
            }
        }
    }

    /**
     * 获取所有有效文件记录的MD5集合（排除挂载文件）
     *
     * @return 有效MD5集合
     */
    private Set<String> getAllValidMd5Set() {
        Set<String> md5Set = new HashSet<>();

        // 获取公共网盘的MD5
        collectMd5ByUid(0L, md5Set);

        // 遍历所有用户，收集每个用户的文件MD5
        forEachUser(user -> collectMd5ByUid(user.getId(), md5Set));
        return md5Set;
    }

    /**
     * 收集指定用户下所有文件的MD5
     *
     * @param uid    用户ID
     * @param md5Set 接收MD5的集合
     */
    private void collectMd5ByUid(Long uid, Set<String> md5Set) {
        FileTree fileTree = fileRecordService.getFullTree(uid);
        for (FileInfo node : fileTree) {
            if (interrupted.get()) {
                break;
            }
            List<FileInfo> files = fileRecordService.findByUidAndNodeId(uid, node.getMd5());
            for (FileInfo file : files) {
                if (!Boolean.TRUE.equals(file.getIsMount()) && file.getMd5() != null) {
                    md5Set.add(file.getMd5());
                }
            }
        }
    }
}
