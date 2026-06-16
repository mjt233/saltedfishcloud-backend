package com.sfc.dm.service;

import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.model.dto.ClaimParam;
import com.sfc.dm.model.po.ClaimRecord;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.ClaimRecordRepo;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;

import java.io.IOException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 认领服务（仅UNIQUE模式）
 */
@Slf4j
@Getter
@Setter
public class ClaimService {
    @Autowired
    private ClaimRecordRepo claimRecordRepo;
    @Autowired
    private InvalidDataRecordRepo invalidDataRecordRepo;
    @Autowired
    private DiskFileSystemManager fileSystemManager;
    @Autowired
    private FileRecordService fileRecordService;
    @Autowired
    private StoreServiceFactory storeServiceFactory;

    /**
     * 认领失效数据
     */
    public void claim(ClaimParam param) throws IOException {
        // 1. 查询失效数据记录
        Long operatorUid = SecureUtils.getCurrentUid();
        InvalidDataRecord record = invalidDataRecordRepo.findById(param.getInvalidDataId())
                .orElseThrow(() -> new IllegalArgumentException("失效数据记录不存在"));

        // 2. 状态校验
        if(!SecureUtils.getSpringSecurityUser().isAdmin()) {
            // 非管理员认领时需要校验已发布可认领状态
            if (record.getStatus() != InvalidDataStatus.PUBLISHED) {
                throw new IllegalStateException("当前状态不允许认领");
            }
        }

        // 3. 权限校验
        Long targetUid = param.getTargetUid();
        if (Objects.equals(targetUid, operatorUid)) {
            // 非自己的网盘仅管理员可操作
            UIDValidator.validateWithException(targetUid, true);
        }

        // 4. 文件名冲突检查
        String diskPath = buildDiskPath(targetUid, param.getSavePath(), param.getFileName());
        DiskFileSystem fs = fileSystemManager.getMainFileSystem();
        try {
            if (fs.exist(targetUid, diskPath)) {
                throw new IllegalStateException("文件名已存在，请修改后重试");
            }
        } catch (IOException e) {
            throw new IllegalStateException("检查文件名冲突失败: " + e.getMessage());
        }

        // 5. 创建文件记录
        if (!StringUtils.hasText(record.getMd5())) {
            throw new IllegalArgumentException("失效数据记录缺少MD5信息，无法认领");
        }
        Storage storage = storeServiceFactory.getService().getStorageProvider();
        FileInfo fileInfo = FileInfo.createFrom(storage.getFileInfo(record.getStoragePath()), false);
        fileInfo.setMd5(record.getMd5());
        fileInfo.setName(param.getFileName());
        fileInfo.setUid(targetUid);
        fileInfo.setNode(null);
        fileInfo.setId(null);
        fileInfo.setUpdateAt(null);
        fileInfo.setCreateAt(null);
        fileInfo.setPath(param.getFileName());
        fileRecordService.saveRecord(fileInfo, param.getSavePath());

        // 6. 创建认领记录
        ClaimRecord claimRecord = new ClaimRecord();
        claimRecord.setUid(operatorUid);
        claimRecord.setInvalidDataId(param.getInvalidDataId());
        claimRecord.setTargetUid(targetUid);
        claimRecord.setFileName(param.getFileName());
        claimRecord.setSavePath(param.getSavePath());
        claimRecordRepo.save(claimRecord);

        // 7. 更新失效数据状态
        record.setStatus(InvalidDataStatus.CLAIMED);
        invalidDataRecordRepo.save(record);
    }

    /**
     * 查询某条失效数据的所有认领记录
     */
    public List<ClaimRecord> listByInvalidDataId(Long invalidDataId) {
        return claimRecordRepo.findByInvalidDataId(invalidDataId);
    }

    /**
     * 查询用户的认领记录
     */
    public CommonPageInfo<ClaimRecord> listByUid(Long uid, Pageable pageable) {
        Page<ClaimRecord> page = claimRecordRepo.findByUid(uid, pageable);
        return CommonPageInfo.of(page);
    }

    /**
     * 构建网盘路径
     */
    private String buildDiskPath(Long targetUid, String savePath, String fileName) {
        String basePath = targetUid == 0 ? "/public" : "/user";
        if (savePath != null && !savePath.isEmpty()) {
            basePath = savePath.startsWith("/") ? savePath : "/" + savePath;
        }
        return basePath.endsWith("/") ? basePath + fileName : basePath + "/" + fileName;
    }
}
