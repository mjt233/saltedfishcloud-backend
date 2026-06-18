package com.sfc.dm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.enums.InvalidDataType;
import com.sfc.dm.enums.ProcessMethod;
import com.sfc.dm.model.dto.BatchResult;
import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.dto.InvalidDataQuery;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Autowired
    private FileRecordService fileRecordService;

    /**
     * 列出所有待处理的失效数据记录中需要识别的记录
     */
    public List<InvalidDataRecord> listNeedIdentify() {
        return repo.findAll(JpaLambdaQueryWrapper.get(InvalidDataRecord.class)
                        .eq(InvalidDataRecord::getStatus, InvalidDataStatus.PENDING)
                        .eq(InvalidDataRecord::getNeedIdentify, true)
                .build());
    }

    /**
     * 查询已发布可认领的失效数据列表（分页+筛选，状态强制为PUBLISHED）
     */
    public CommonPageInfo<InvalidDataRecord> listPublished(InvalidDataQuery query, PageableRequest pageable) {
        query.setStatus(List.of(InvalidDataStatus.PUBLISHED.name()));
        return list(query, pageable);
    }

    /**
     * 查询失效数据列表（分页+筛选）
     */
    public CommonPageInfo<InvalidDataRecord> list(InvalidDataQuery query, PageableRequest pageable) {
        JpaLambdaQueryWrapper<InvalidDataRecord> wrapper = JpaLambdaQueryWrapper.get(InvalidDataRecord.class);
        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            wrapper.in(InvalidDataRecord::getStatus, query.getStatus());
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
        if (query.getFileType() != null && !query.getFileType().isEmpty()) {
            wrapper.in(InvalidDataRecord::getFileType, query.getFileType());
        }
        Sort sort = buildSort(query);
        return CommonPageInfo.of(repo.findAll(wrapper.build(), PageRequest.of(pageable.getPage(), pageable.getSize(), sort)));
    }

    /**
     * 获取失效数据详情
     */
    public InvalidDataRecord getDetail(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("失效数据记录不存在"));
    }

    /**
     * 下载失效数据内容。
     * <p>可下载条件：</p>
     * <ul>
     *     <li>状态不为"处理完成"</li>
     *     <li>类型为"失效的物理存储" 或 (类型为"失效的文件记录" 且 有md5值 且 系统存在该md5的有效物理存储)</li>
     * </ul>
     *
     * @param id 失效数据记录ID
     * @return 响应实体，包含文件资源和正确的Content-Type
     */
    public ResponseEntity<Resource> getDownloadResource(Long id) throws IOException {
        InvalidDataRecord record = getDetail(id);

        if (record.getStatus() == InvalidDataStatus.COMPLETED) {
            throw new IllegalStateException("已处理完成的数据不可下载");
        }

        boolean downloadable = record.getType() == InvalidDataType.PHYSICAL_STORAGE
                || (record.getType() == InvalidDataType.FILE_RECORD
                    && record.getMd5() != null
                    && !fileInfoRepo.findByMd5(record.getMd5(), PageRequest.of(0, 1)).isEmpty());

        if (!downloadable) {
            throw new IllegalStateException("该数据当前不满足下载条件");
        }

        Storage storage = storeServiceFactory.getService().getStorageProvider();
        Resource resource = storage.getResource(record.getStoragePath());
        if (resource == null) {
            throw new IllegalStateException("物理存储文件不存在");
        }

        String fileName = PathUtils.getLastNode(record.getStoragePath());
        String contentType = resolveContentType(record, fileName);
        String disposition = ResourceUtils.generateContentDisposition(fileName);

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", disposition)
                .body(resource);
    }

    /**
     * 解析失效数据的Content-Type。
     * 优先取typeCheckResult中的mimetype，否则按物理存储文件扩展名获取。
     */
    private String resolveContentType(InvalidDataRecord record, String fileName) {
        if (record.getTypeCheckResult() != null) {
            try {
                FileTypeCheckResult result = new ObjectMapper().readValue(record.getTypeCheckResult(), FileTypeCheckResult.class);
                if (result.getDetail() != null && result.getDetail().getMimetype() != null) {
                    return result.getDetail().getMimetype();
                }
            } catch (IOException ignored) {
            }
        }
        return FileUtils.getContentType(fileName);
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
     * 批量发布为可认领
     */
    public BatchResult publish(List<Long> idList) {
        return batchUpdateStatus(idList, InvalidDataStatus.PENDING, InvalidDataStatus.PUBLISHED, "发布");
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
     * 批量取消发布
     */
    public BatchResult unpublish(List<Long> idList) {
        return batchUpdateStatus(idList, InvalidDataStatus.PUBLISHED, InvalidDataStatus.PENDING, "取消发布");
    }

    /**
     * 批量快速修复（RAW模式）
     */
    public BatchResult quickFix(List<Long> ids) {
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
        return new BatchResult(success, fail, errors);
    }

    /**
     * 修复所有待处理的失效数据
     */
    public BatchResult quickFixAll() {
        List<Long> ids = repo.findIdsByStatus(InvalidDataStatus.PENDING);
        return quickFix(ids);
    }

    /**
     * 批量丢弃
     */
    public BatchResult discard(List<Long> ids) {
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
        return new BatchResult(success, fail, errors);
    }

    /**
     * 丢弃所有可丢弃的数据
     */
    public BatchResult discardAll() {
        List<Long> ids = repo.findIdsByStatus(InvalidDataStatus.PENDING);
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
        try {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setPath(PathUtils.getParentPath(record.getDiskPath()));
            fileInfo.setUid(record.getOwnerUid());
            fileInfo.setName(PathUtils.getLastNode(record.getDiskPath()));
            fileInfo.setSize(record.getFileSize());
            fileInfo.setMtime(record.getLastModified().getTime());
            fileInfo.setCtime(record.getLastModified().getTime());
            if (record.getMd5() == null) {
                fileInfo.setStreamSource(() -> storage.getResource(record.getStoragePath()).getInputStream());
                fileInfo.updateMd5();
            }
            fileRecordService.saveRecord(fileInfo, fileInfo.getPath());
        } catch (IOException e) {
            throw new IllegalStateException("重建文件记录失败: " + e.getMessage());
        }
        record.setStatus(InvalidDataStatus.COMPLETED);
        record.setProcessMethod(ProcessMethod.AUTO_REPAIR);
        repo.save(record);
    }

    /**
     * 修复失效文件记录：尝试按 md5 查询已存在的相同文件，保存到物理存储
     */
    private void quickFixFileRecord(InvalidDataRecord record) {
        DiskFileSystem fs = fileSystemManager.getMainFileSystem();
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
                    if (resource == null) {
                        continue;
                    }
                    try (InputStream is = resource.getInputStream()) {
                        storage.store(source, record.getStoragePath(), source.getSize(), is);
                        copied = true;
                        break;
                    }
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
     * 批量更新状态（分批执行，每批200条）
     *
     * @param idList      待更新的ID列表
     * @param fromStatus  允许的源状态（校验用）
     * @param toStatus    目标状态
     * @param actionName  操作名称（用于错误信息）
     * @return 批量操作结果
     */
    private BatchResult batchUpdateStatus(List<Long> idList, InvalidDataStatus fromStatus, InvalidDataStatus toStatus, String actionName) {
        List<InvalidDataRecord> records = repo.findAllById(idList);

        Map<Long, InvalidDataRecord> recordMap = records.stream()
                .collect(Collectors.toMap(InvalidDataRecord::getId, r -> r));

        List<Long> validIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Long id : idList) {
            InvalidDataRecord record = recordMap.get(id);
            if (record == null) {
                errors.add("ID " + id + ": 记录不存在");
            } else if (record.getStatus() != fromStatus) {
                errors.add("ID " + id + ": 当前状态不允许" + actionName);
            } else {
                validIds.add(id);
            }
        }

        int success = 0;
        for (int i = 0; i < validIds.size(); i += BATCH_SIZE) {
            List<Long> batch = validIds.subList(i, Math.min(i + BATCH_SIZE, validIds.size()));
            success += repo.updateStatusByIds(batch, toStatus);
        }

        return new BatchResult(success, errors.size(), errors);
    }

    private static final int BATCH_SIZE = 200;

    /**
     * 构建排序条件（仅允许 fileSize、lastModified 字段）
     */
    private Sort buildSort(InvalidDataQuery query) {
        if (query.getSortBy() == null || query.getSortBy().isBlank()) {
            return Sort.unsorted();
        }
        String field = query.getSortBy();
        if (!"fileSize".equals(field) && !"lastModified".equals(field)) {
            throw new IllegalArgumentException("不支持的排序字段: " + field);
        }
        Sort.Direction direction = "ASC".equalsIgnoreCase(query.getSortOrder())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
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
