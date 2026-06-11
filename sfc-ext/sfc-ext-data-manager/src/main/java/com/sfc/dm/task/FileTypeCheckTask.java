package com.sfc.dm.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.sfc.dm.service.identify.FileTypeChecker;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文件类型识别异步任务
 */
@Slf4j
public class FileTypeCheckTask implements AsyncTask {
    @Setter private InvalidDataRecordRepo invalidDataRepo;
    @Setter private StoreServiceFactory storeServiceFactory;
    @Setter private FileTypeChecker fileTypeChecker;

    private PrintWriter logWriter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private final AtomicReference<Thread> executeThread = new AtomicReference<>();
    private final ProgressRecord progressRecord = new ProgressRecord();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            // 1. 查询所有待识别的失效数据记录
            List<InvalidDataRecord> records = invalidDataRepo.findAll().stream()
                    .filter(r -> r.getStatus() == InvalidDataStatus.PENDING)
                    .filter(r -> Boolean.TRUE.equals(r.getNeedIdentify()))
                    .toList();

            log("待识别文件数量: " + records.size());
            progressRecord.setTotal(records.size()).setLoaded(0);

            Storage storage = storeServiceFactory.getService().getStorageProvider();
            int success = 0;
            int fail = 0;

            // 2. 逐个识别
            for (int i = 0; i < records.size(); i++) {
                if (interrupted.get()) {
                    log("任务被中断");
                    break;
                }
                InvalidDataRecord record = records.get(i);
                try {
                    boolean identified = identifyFile(storage, record);
                    if (identified) {
                        success++;
                    } else {
                        fail++;
                        log("无法识别: " + record.getStoragePath());
                    }
                } catch (Exception e) {
                    fail++;
                    log("识别失败 [" + record.getStoragePath() + "]: " + e.getMessage());
                }
                progressRecord.setLoaded(i + 1);
            }

            log("识别完成，成功: " + success + "，失败: " + fail);
        } catch (Exception e) {
            log("识别任务异常: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            running.set(false);
            executeThread.set(null);
            logWriter.flush();
        }
    }

    /**
     * 识别单个文件
     */
    private boolean identifyFile(Storage storage, InvalidDataRecord record) throws IOException {
        String storagePath = record.getStoragePath();
        Resource resource = storage.getResource(storagePath);
        if (resource == null) {
            return false;
        }

        // 下载到临时文件
        File tempFile = downloadToTemp(resource, storagePath);
        try {
            FileTypeCheckResult result = fileTypeChecker.checkFile(tempFile, true);
            if (result != null) {
                record.setFileType(result.getTypeId());
                if (result.getDetail() != null && result.getDetail().getMetadata() != null) {
                    record.setMetadata(objectMapper.writeValueAsString(result.getDetail().getMetadata()));
                }
                record.setNeedIdentify(false);
                invalidDataRepo.save(record);
                log("识别成功 [" + storagePath + "] -> " + result.getTypeName());
                return true;
            }
            return false;
        } finally {
            tempFile.delete();
        }
    }

    /**
     * 将Resource下载到临时文件
     */
    private File downloadToTemp(Resource resource, String storagePath) throws IOException {
        String suffix = "";
        int dotIndex = storagePath.lastIndexOf('.');
        if (dotIndex >= 0) {
            suffix = storagePath.substring(dotIndex);
        }
        File tempFile = Files.createTempFile("dm-identify-", suffix).toFile();
        try (InputStream in = resource.getInputStream();
             OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (interrupted.get()) break;
                out.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
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
