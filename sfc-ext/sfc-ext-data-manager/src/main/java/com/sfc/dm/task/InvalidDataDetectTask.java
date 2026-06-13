package com.sfc.dm.task;

import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.enums.InvalidDataType;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 失效数据检测异步任务
 */
@Slf4j
public class InvalidDataDetectTask implements AsyncTask {
    @Setter
    private InvalidDataRecordRepo invalidDataRepo;
    @Setter
    private StoreServiceFactory storeServiceFactory;
    @Setter
    private DiskFileSystemManager fileSystemManager;
    @Setter
    private FileInfoRepo fileInfoRepo;
    @Setter
    private SysCommonConfig sysCommonConfig;
    @Setter
    private SysProperties sysProperties;
    @Setter
    private UserService userService;

    private PrintWriter logWriter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private final AtomicReference<Thread> executeThread = new AtomicReference<>();
    private final ProgressRecord progressRecord = new ProgressRecord();

    private void log(String message) {
        if (logWriter != null) {
            logWriter.println(message);
            logWriter.flush();
        }
    }

    @Override
    public void execute(OutputStream logOutputStream) {
        if (running.get()) {
            throw new IllegalStateException("任务已在运行中");
        }
        running.set(true);
        executeThread.set(Thread.currentThread());
        logWriter = new PrintWriter(logOutputStream);
        try {
            // 1. 清除上一轮待处理记录
            log("清除上一轮待处理的检测结果...");
            int deleted = invalidDataRepo.deleteByStatus(InvalidDataStatus.PENDING);
            log("已清除 " + deleted + " 条待处理记录");

            // 2. 根据存储模式执行扫描
            StoreMode mode = sysCommonConfig.getStoreMode();
            List<InvalidDataRecord> results;
            if (mode == StoreMode.RAW) {
                results = scanRawMode();
            } else {
                results = scanUniqueMode();
            }

            // 3. 批量保存检测结果
            progressRecord.setTotal(results.size()).setLoaded(0);
            log("开始保存检测结果，共 " + results.size() + " 条...");
            for (int i = 0; i < results.size(); i++) {
                if (interrupted.get()) {
                    log("任务被中断");
                    break;
                }
                invalidDataRepo.save(results.get(i));
                progressRecord.setLoaded(i + 1);
            }
            log("检测完成，共发现 " + results.size() + " 条失效数据");
        } catch (Exception e) {
            log("检测异常: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            running.set(false);
            executeThread.set(null);
            logWriter.flush();
        }
    }

    /**
     * RAW模式扫描
     */
    private List<InvalidDataRecord> scanRawMode() {
        log("开始RAW模式扫描...");
        List<InvalidDataRecord> results = new ArrayList<>();
        Storage storage = storeServiceFactory.getService().getStorageProvider();

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
        int page = 0;
        int pageSize = 100;
        while (true) {
            if (interrupted.get()) {
                break;
            }
            PageableRequest pageableRequest = new PageableRequest();
            pageableRequest.setPage(page);
            pageableRequest.setSize(pageSize);
            CommonPageInfo<User> userPage = userService.listUsers(pageableRequest);
            if (userPage.getContent() == null || userPage.getContent().isEmpty()) {
                break;
            }
            for (User user : userPage.getContent()) {
                if (interrupted.get()) {
                    break;
                }
                String userDir = StringUtils.appendPath(userFileRoot, user.getId().toString());
                log("扫描用户网盘: uid=" + user.getId());
                try {
                    scanDirectory(storage, userDir, "/", user.getId(), results);
                } catch (IOException e) {
                    log("扫描用户网盘失败 [" + userDir + "]: " + e.getMessage());
                }
            }
            if (page >= userPage.getTotalPage() - 1) {
                break;
            }
            page++;
        }
        log("RAW模式扫描完成，发现 " + results.size() + " 条失效数据");
        return results;
    }

    /**
     * UNIQUE模式扫描
     */
    private List<InvalidDataRecord> scanUniqueMode() {
        log("开始UNIQUE模式扫描...");
        List<InvalidDataRecord> results = new ArrayList<>();
        Storage storage = storeServiceFactory.getService().getStorageProvider();
        String storeRoot = sysProperties.getStore().getRoot();

        // 获取所有文件记录的MD5集合
        log("获取所有文件记录的MD5...");
        Set<String> validMd5Set = getAllValidMd5Set();
        log("有效MD5数量: " + validMd5Set.size());

        // 扫描storeRoot下所有无拓展名的物理存储文件
        log("扫描物理存储目录: " + storeRoot);
        try {
            scanUniqueStorage(storage, storeRoot, validMd5Set, results);
        } catch (IOException e) {
            log("扫描物理存储目录失败: " + e.getMessage());
        }

        log("UNIQUE模式扫描完成，发现 " + results.size() + " 条失效数据");
        return results;
    }

    /**
     * RAW模式：扫描目录下的文件，检查文件记录是否存在
     *
     * @param storage   物理存储
     * @param path      物理存储的扫描路径
     * @param diskRoot  对应的DiskFileSystem根路径，用于计算磁盘相对路径
     * @param ownerUid  查询文件记录的文件所属用户id（公共网盘为0）
     * @param results   接收的结果数组
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
                        InvalidDataRecord invalidRecord = createInvalidRecord(
                                fullPath, ownerUid, diskPath, file.getSize(), new Date(file.getMtime()), file.getMd5());
                        results.add(invalidRecord);
                    }
                } catch (IOException e) {
                    log("检查文件记录失败 [" + fullPath + "]: " + e.getMessage());
                }
            }
        }
    }

    /**
     * UNIQUE模式：扫描物理存储文件，检查MD5是否有对应文件记录
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
                        InvalidDataRecord invalidRecord = createInvalidRecord(
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
     */
    private Set<String> getAllValidMd5Set() {
        // 通过FileInfoRepo获取所有文件记录的MD5，排除挂载文件
        List<FileInfo> allFiles = fileInfoRepo.findAll();
        return allFiles.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsMount()))
                .map(FileInfo::getMd5)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 创建失效数据记录
     */
    private InvalidDataRecord createInvalidRecord(String storagePath, Long ownerUid,
                                                  String diskPath, Long fileSize,
                                                  Date lastModified, String md5) {
        InvalidDataRecord record = new InvalidDataRecord();
        record.setType(InvalidDataType.PHYSICAL_STORAGE);
        record.setStoragePath(storagePath);
        record.setOwnerUid(ownerUid);
        record.setDiskPath(diskPath);
        record.setFileSize(fileSize);
        record.setLastModified(lastModified);
        record.setNeedIdentify(false);
        record.setStatus(InvalidDataStatus.PENDING);
        record.setMd5(md5);
        record.setStoreMode(sysCommonConfig.getStoreMode());
        return record;
    }

    @Override
    public void interrupt() {
        if (running.get()) {
            interrupted.set(true);
            Thread thread = executeThread.get();
            if (thread != null) {
                thread.interrupt();
            }
            log("任务被中断");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public String getParams() {
        return null;
    }

    @Override
    public ProgressRecord getProgress() {
        return progressRecord;
    }
}
