package com.sfc.dm.service;

import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.enums.InvalidDataType;
import com.sfc.dm.enums.ProcessMethod;
import com.sfc.dm.model.dto.InvalidDataQuery;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 失效数据服务
 */
@Slf4j
@Getter
@Setter
public class InvalidDataService {
    @Autowired
    private InvalidDataRecordRepo repo;
    @Autowired
    private StoreServiceFactory storeServiceFactory;
    @Autowired
    private DiskFileSystemManager fileSystemManager;
    @Autowired
    private FileInfoRepo fileInfoRepo;
    @Autowired
    private SysCommonConfig sysCommonConfig;

    /**
     * 查询失效数据列表（分页+筛选）
     */
    public CommonPageInfo<InvalidDataRecord> list(InvalidDataQuery query, Pageable pageable) {
        JpaLambdaQueryWrapper<InvalidDataRecord> wrapper = JpaLambdaQueryWrapper.get(InvalidDataRecord.class);
        if (query.getStatus() != null) {
            wrapper.eq(InvalidDataRecord::getStatus, query.getStatus());
        }
        if (query.getOwnerUid() != null) {
            wrapper.eq(InvalidDataRecord::getOwnerUid, query.getOwnerUid());
        }
        if (query.getMinFileSize() != null) {
            wrapper.ge(InvalidDataRecord::getFileSize, query.getMinFileSize());
        }
        if (query.getMaxFileSize() != null) {
            wrapper.le(InvalidDataRecord::getFileSize, query.getMaxFileSize());
        }
        if (query.getFileType() != null) {
            wrapper.eq(InvalidDataRecord::getFileType, query.getFileType());
        }
        return CommonPageInfo.of(repo.findAll(wrapper.build(), pageable));
    }

    /**
     * 获取失效数据详情
     */
    public InvalidDataRecord getDetail(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("失效数据记录不存在"));
    }

    /**
     * 发布为可认领（UNIQUE模式，PENDING -> PUBLISHED）
     */
    public void publish(Long id) {
        InvalidDataRecord record = getDetail(id);
        if (record.getStatus() != InvalidDataStatus.PENDING) {
            throw new IllegalStateException("当前状态不允许发布");
        }
        record.setStatus(InvalidDataStatus.PUBLISHED);
        repo.save(record);
    }

    /**
     * 取消发布（UNIQUE模式，PUBLISHED -> PENDING）
     */
    public void unpublish(Long id) {
        InvalidDataRecord record = getDetail(id);
        if (record.getStatus() != InvalidDataStatus.PUBLISHED) {
            throw new IllegalStateException("当前状态不允许取消发布");
        }
        record.setStatus(InvalidDataStatus.PENDING);
        repo.save(record);
    }

    /**
     * 批量快速修复（RAW模式）
     */
    public Map<String, Object> quickFix(List<Long> ids) {
        int success = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                quickFixSingle(id);
                success++;
            } catch (Exception e) {
                fail++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }
        return Map.of("success", success, "fail", fail, "errors", errors);
    }

    /**
     * 修复所有待处理的失效数据
     */
    public Map<String, Object> quickFixAll() {
        List<Long> ids = repo.findIdsByStatus(InvalidDataStatus.PENDING.name());
        return quickFix(ids);
    }

    /**
     * 批量丢弃
     */
    public Map<String, Object> discard(List<Long> ids) {
        int success = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                discardSingle(id);
                success++;
            } catch (Exception e) {
                fail++;
                errors.add("ID " + id + ": " + e.getMessage());
            }
        }
        return Map.of("success", success, "fail", fail, "errors", errors);
    }

    /**
     * 丢弃所有可丢弃的数据
     */
    public Map<String, Object> discardAll() {
        List<Long> ids = repo.findIdsByStatus(InvalidDataStatus.PENDING.name());
        return discard(ids);
    }

    /**
     * 标记处理完成（UNIQUE模式，CLAIMED -> COMPLETED(CLAIM)）
     */
    public void markCompleted(Long id) {
        InvalidDataRecord record = getDetail(id);
        if (record.getStatus() != InvalidDataStatus.CLAIMED) {
            throw new IllegalStateException("当前状态不允许标记处理完成");
        }
        record.setStatus(InvalidDataStatus.COMPLETED);
        record.setProcessMethod(ProcessMethod.CLAIM);
        repo.save(record);
    }

    /**
     * 单个快速修复
     */
    private void quickFixSingle(Long id) {
        InvalidDataRecord record = getDetail(id);
        if (record.getStatus() != InvalidDataStatus.PENDING) {
            throw new IllegalStateException("当前状态不允许快速修复");
        }
        if (record.getType() == InvalidDataType.PHYSICAL_STORAGE) {
            quickFixPhysicalStorage(record);
        } else {
            quickFixFileRecord(record);
        }
    }

    /**
     * 修复失效物理存储：重建文件记录
     */
    private void quickFixPhysicalStorage(InvalidDataRecord record) {
        Storage storage = storeServiceFactory.getService().getStorageProvider();
        try {
            if (!storage.exist(record.getStoragePath())) {
                throw new IllegalStateException("物理存储文件不存在");
            }
        } catch (IOException e) {
            throw new IllegalStateException("检查物理存储失败: " + e.getMessage());
        }
        DiskFileSystem fs = fileSystemManager.getMainFileSystem();
        try {
            fs.quickSave(record.getOwnerUid(), extractParentPath(record.getDiskPath()),
                    extractFileName(record.getDiskPath()), record.getMd5());
        } catch (IOException e) {
            throw new IllegalStateException("重建文件记录失败: " + e.getMessage());
        }
        record.setStatus(InvalidDataStatus.COMPLETED);
        record.setProcessMethod(ProcessMethod.AUTO_REPAIR);
        repo.save(record);
    }

    /**
     * 修复失效文件记录
     */
    private void quickFixFileRecord(InvalidDataRecord record) {
        DiskFileSystem fs = fileSystemManager.getMainFileSystem();
        try {
            // 1. 检查原路径是否可用
            if (fs.exist(record.getOwnerUid(), record.getDiskPath())) {
                throw new IllegalStateException("原路径已被占用");
            }
        } catch (IOException e) {
            throw new IllegalStateException("检查路径失败: " + e.getMessage());
        }
        Storage storage = storeServiceFactory.getService().getStorageProvider();
        boolean storageExists;
        try {
            storageExists = storage.exist(record.getStoragePath());
        } catch (IOException e) {
            throw new IllegalStateException("检查物理存储失败: " + e.getMessage());
        }
        if (storageExists) {
            // 2. 物理存储存在，重建文件记录
            try {
                fs.quickSave(record.getOwnerUid(), extractParentPath(record.getDiskPath()),
                        extractFileName(record.getDiskPath()), record.getMd5());
            } catch (IOException e) {
                throw new IllegalStateException("重建文件记录失败: " + e.getMessage());
            }
        } else {
            // 3. 物理存储不存在，通过md5查找并逐个尝试复制
            List<FileInfo> sameMd5Files = fileInfoRepo.findByMd5(record.getMd5(), PageRequest.of(0, 10)).getContent();
            if (sameMd5Files.isEmpty()) {
                throw new IllegalStateException("物理存储不存在且无相同MD5文件");
            }
            boolean copied = false;
            for (FileInfo source : sameMd5Files) {
                try {
                    Resource resource = fs.getResource(source.getUid(), extractParentPath(source.getName()), source.getName());
                    if (resource == null) continue;
                    storage.store(source, record.getStoragePath(), source.getSize(), resource.getInputStream());
                    copied = true;
                    break;
                } catch (IOException ignored) {
                }
            }
            if (!copied) {
                throw new IllegalStateException("物理存储不存在，且无法从相同MD5文件复制");
            }
        }
        record.setStatus(InvalidDataStatus.COMPLETED);
        record.setProcessMethod(ProcessMethod.AUTO_REPAIR);
        repo.save(record);
    }

    /**
     * 单个丢弃
     */
    private void discardSingle(Long id) {
        InvalidDataRecord record = getDetail(id);
        if (record.getStatus() != InvalidDataStatus.PENDING) {
            throw new IllegalStateException("当前状态不允许丢弃");
        }
        if (record.getType() == InvalidDataType.PHYSICAL_STORAGE) {
            discardPhysicalStorage(record);
        } else {
            discardFileRecord(record);
        }
    }

    /**
     * 丢弃失效物理存储：删除物理文件
     */
    private void discardPhysicalStorage(InvalidDataRecord record) {
        Storage storage = storeServiceFactory.getService().getStorageProvider();
        try {
            storage.delete(record.getStoragePath());
        } catch (IOException e) {
            throw new IllegalStateException("删除物理存储失败: " + e.getMessage());
        }
        record.setStatus(InvalidDataStatus.COMPLETED);
        record.setProcessMethod(ProcessMethod.DISCARD);
        repo.save(record);
    }

    /**
     * 丢弃失效文件记录：删除记录本身
     */
    private void discardFileRecord(InvalidDataRecord record) {
        record.setStatus(InvalidDataStatus.COMPLETED);
        record.setProcessMethod(ProcessMethod.DISCARD);
        repo.save(record);
    }

    /**
     * 从路径中提取文件名
     */
    private String extractFileName(String path) {
        if (path == null) return null;
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String extractParentPath(String path) {
        if (path == null) return "/";
        int lastSlash = path.lastIndexOf('/');
        return lastSlash > 0 ? path.substring(0, lastSlash) : "/";
    }
}
