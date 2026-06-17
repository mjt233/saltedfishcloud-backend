package com.sfc.dm.task.detect;

import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.dm.task.detect.scanner.InvalidDataScanner;
import com.sfc.dm.task.detect.scanner.RawModeScanner;
import com.sfc.dm.task.detect.scanner.UniqueModeScanner;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.xiaotao.saltedfishcloud.utils.DBUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 失效数据检测异步任务
 */
@Slf4j
public class InvalidDataDetectTask implements AsyncTask {
    private static final int BATCH_SIZE = 200;

    @Setter
    private InvalidDataRecordRepo invalidDataRepo;
    @Setter
    private StoreServiceFactory storeServiceFactory;
    @Setter
    private DiskFileSystemManager fileSystemManager;
    @Setter
    private FileRecordService fileRecordService;
    @Setter
    private SysCommonConfig sysCommonConfig;
    @Setter
    private SysProperties sysProperties;
    @Setter
    private UserService userService;
    @Setter
    private JdbcTemplate jdbcTemplate;

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

    /**
     * 根据存储模式创建对应的扫描器
     *
     * @return 扫描器实例
     */
    private InvalidDataScanner createScanner() {
        StoreMode mode = sysCommonConfig.getStoreMode();
        if (mode == StoreMode.RAW) {
            RawModeScanner scanner = new RawModeScanner();
            scanner.setStoreServiceFactory(storeServiceFactory);
            scanner.setSysProperties(sysProperties);
            scanner.setUserService(userService);
            scanner.setSysCommonConfig(sysCommonConfig);
            scanner.setLogWriter(logWriter);
            scanner.setInterrupted(interrupted);
            scanner.setFileSystemManager(fileSystemManager);
            scanner.setFileRecordService(fileRecordService);
            return scanner;
        } else {
            UniqueModeScanner scanner = new UniqueModeScanner();
            scanner.setStoreServiceFactory(storeServiceFactory);
            scanner.setSysProperties(sysProperties);
            scanner.setUserService(userService);
            scanner.setSysCommonConfig(sysCommonConfig);
            scanner.setLogWriter(logWriter);
            scanner.setInterrupted(interrupted);
            scanner.setFileRecordService(fileRecordService);
            return scanner;
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

            // 2. 流式扫描并批量保存检测结果
            InvalidDataScanner scanner = createScanner();
            log("开始扫描...");
            AtomicInteger savedCount = new AtomicInteger(0);
            List<InvalidDataRecord> batchBuffer = new ArrayList<>();
            scanner.scan(record -> {
                batchBuffer.add(record);
                if (batchBuffer.size() >= BATCH_SIZE) {
                    DBUtils.batchInsert(jdbcTemplate, batchBuffer);
                    savedCount.addAndGet(batchBuffer.size());
                    log("已保存 " + savedCount.get() + " 条");
                    batchBuffer.clear();
                }
            });
            if (!batchBuffer.isEmpty()) {
                DBUtils.batchInsert(jdbcTemplate, batchBuffer);
                savedCount.addAndGet(batchBuffer.size());
                batchBuffer.clear();
            }
            log("检测完成，共发现 " + savedCount.get() + " 条失效数据");
        } catch (Exception e) {
            log("检测异常: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            running.set(false);
            executeThread.set(null);
            logWriter.flush();
        }
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
