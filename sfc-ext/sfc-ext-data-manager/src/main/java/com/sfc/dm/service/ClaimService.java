package com.sfc.dm.service;

import com.sfc.dm.constant.InvalidDataError;
import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.enums.InvalidDataType;
import com.sfc.dm.model.dto.BatchClaimParam;
import com.sfc.dm.model.dto.BatchResult;
import com.sfc.dm.model.dto.ClaimParam;
import com.sfc.dm.model.dto.ClaimPreviewItem;
import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.dto.InvalidDataQuery;
import com.sfc.dm.model.po.ClaimRecord;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.ClaimRecordRepo;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileRecordService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    @Autowired
    private InvalidDataService invalidDataService;

    /**
     * 认领失效数据
     */
    public void claim(ClaimParam param) throws IOException {
        // 1. 查询失效数据记录
        Long operatorUid = SecureUtils.getCurrentUid();
        InvalidDataRecord record = invalidDataRecordRepo.findById(param.getInvalidDataId())
                .orElseThrow(() -> new JsonException(InvalidDataError.INVALID_DATA_NOT_FOUND));
        if (record.getType() != InvalidDataType.PHYSICAL_STORAGE) {
            throw new JsonException(InvalidDataError.ONLY_INVALID_STORAGE_CLAIMABLE);
        }

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

    /**
     * 批量认领预览。
     * <p>查询匹配条件的可认领失效数据（UNIQUE 模式 + 失效物理存储），
     * 解析每条记录认领后的保存路径与文件名，最多返回前 10 条。</p>
     *
     * @param param 批量认领参数（含筛选条件、目标用户、保存路径、可选脚本）
     * @return 预览结果列表（最多10条）
     */
    @Transactional(readOnly = true)
    public List<ClaimPreviewItem> previewBatchClaim(BatchClaimParam param) {
        List<ClaimPreviewItem> items = new ArrayList<>();
        try (Stream<InvalidDataRecord> stream = invalidDataService.streamClaimableRecords(param.getQuery())) {
            try (GroovyScriptExecutor executor = createPathScriptExecutor(param.getScript())) {
                java.util.Iterator<InvalidDataRecord> iterator = stream.iterator();
                while (iterator.hasNext() && items.size() < 10) {
                    InvalidDataRecord record = iterator.next();
                    String[] resolved = resolvePathAndName(param, record, executor);
                    ClaimPreviewItem item = new ClaimPreviewItem();
                    item.setInvalidDataId(record.getId());
                    item.setOriginalFileName(PathUtils.getLastNode(record.getStoragePath()));
                    item.setResolvedPath(resolved[0]);
                    item.setResolvedFileName(resolved[1]);
                    item.setFileType(record.getFileType());
                    item.setFileSize(record.getFileSize());
                    item.setExtension(getRecognizedExtension(record));
                    items.add(item);
                }
            }
        }
        return items;
    }

    /**
     * 批量认领失效数据（仅限管理员调用）。
     * <p>查询匹配条件的可认领失效数据（UNIQUE 模式 + 失效物理存储），
     * 逐条解析保存路径与文件名后执行认领，跳过失败项继续处理后续记录。</p>
     *
     * @param param 批量认领参数（含筛选条件、目标用户、保存路径、可选脚本）
     * @return 批量操作结果（成功数、失败数、错误信息）
     */
    @Transactional
    public BatchResult batchClaim(BatchClaimParam param) {
        int success = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();
        try (Stream<InvalidDataRecord> stream = invalidDataService.streamClaimableRecords(param.getQuery())) {
            try (GroovyScriptExecutor executor = createPathScriptExecutor(param.getScript())) {
                java.util.Iterator<InvalidDataRecord> iterator = stream.iterator();
                while (iterator.hasNext()) {
                    InvalidDataRecord record = iterator.next();
                    try {
                        String[] resolved = resolvePathAndName(param, record, executor);
                        ClaimParam claimParam = new ClaimParam();
                        claimParam.setInvalidDataId(record.getId());
                        claimParam.setTargetUid(param.getTargetUid());
                        claimParam.setSavePath(resolved[0]);
                        claimParam.setFileName(resolved[1]);
                        claim(claimParam);
                        success++;
                    } catch (Exception e) {
                        fail++;
                        errors.add("ID " + record.getId() + ": " + e.getMessage());
                    }
                }
            }
        }
        return new BatchResult(success, fail, errors);
    }

    /**
     * 批量撤回认领。根据查询条件筛选已认领的失效数据记录，
     * 删除对应的文件记录，标记认领记录为已撤回，将失效数据状态恢复为待处理。
     *
     * @param query 失效数据查询参数（status 强制为 CLAIMED，支持 Groovy 脚本过滤）
     * @return 批量操作结果（成功撤回的认领记录数、失败数、错误信息）
     */
    @Transactional
    public BatchResult batchRevoke(InvalidDataQuery query) {
        List<Long> ids = invalidDataService.findIdsByQuery(query, InvalidDataStatus.CLAIMED);

        if (ids.isEmpty()) {
            return new BatchResult(0, 0, List.of("没有匹配的失效数据记录"));
        }

        List<ClaimRecord> activeClaims = claimRecordRepo.findByInvalidDataIdInAndIsRevokedFalse(ids);
        if (activeClaims.isEmpty()) {
            return new BatchResult(0, 0, List.of("没有可撤回的认领记录"));
        }

        int fail = 0;
        List<String> errors = new ArrayList<>();
        List<ClaimRecord> deletableClaims = new ArrayList<>();

        var groups = activeClaims.stream().collect(Collectors.groupingBy(
                c -> c.getTargetUid() + "|" + c.getSavePath()));
        for (var entry : groups.entrySet()) {
            var claims = entry.getValue();
            try {
                ClaimRecord first = claims.getFirst();
                List<String> names = claims.stream().map(ClaimRecord::getFileName).toList();
                fileRecordService.deleteRecords(first.getTargetUid(), first.getSavePath(), names);
                deletableClaims.addAll(claims);
            } catch (Exception e) {
                fail += claims.size();
                ClaimRecord first = claims.getFirst();
                errors.add("删除文件失败(uid=" + first.getTargetUid() + ", path=" + first.getSavePath() + "): " + e.getMessage());
            }
        }

        if (deletableClaims.isEmpty()) {
            return new BatchResult(0, fail, errors);
        }

        List<Long> claimIds = deletableClaims.stream().map(ClaimRecord::getId).toList();
        for (int i = 0; i < claimIds.size(); i += BATCH_SIZE) {
            List<Long> batch = claimIds.subList(i, Math.min(i + BATCH_SIZE, claimIds.size()));
            claimRecordRepo.batchMarkRevoked(batch);
        }

        List<Long> affectedIdList = deletableClaims.stream().map(ClaimRecord::getInvalidDataId).distinct().collect(Collectors.toList());
        for (int i = 0; i < affectedIdList.size(); i += BATCH_SIZE) {
            List<Long> batch = affectedIdList.subList(i, Math.min(i + BATCH_SIZE, affectedIdList.size()));
            invalidDataRecordRepo.updateStatusByIds(batch, InvalidDataStatus.PENDING);
        }

        return new BatchResult(deletableClaims.size(), fail, errors);
    }

    /**
     * 解析认领时的保存路径与文件名。
     *
     * @param param  批量认领参数
     * @param record 失效数据记录
     * @param executor Groovy 脚本执行器（已经编译）
     * @return [0]=savePath, [1]=fileName
     */
    private String[] resolvePathAndName(BatchClaimParam param, InvalidDataRecord record, GroovyScriptExecutor executor) {
        String resolvedPath = param.getSavePath();
        String resolvedName = getDefaultClaimFileName(record);
        if (executor != null) {
            Map<String, String> scriptResult = executePathScript(executor, record);
            if (scriptResult != null) {
                if (StringUtils.hasText(scriptResult.get("path"))) {
                    resolvedPath = scriptResult.get("path");
                }
                if (StringUtils.hasText(scriptResult.get("name"))) {
                    resolvedName = scriptResult.get("name");
                }
            }
        }
        return new String[]{resolvedPath, resolvedName};
    }

    /**
     * 获取默认认领文件名（原始文件名 + 识别的扩展名）。
     */
    private String getDefaultClaimFileName(InvalidDataRecord record) {
        String originalName = PathUtils.getLastNode(record.getStoragePath());
        String extension = getRecognizedExtension(record);
        if (StringUtils.hasText(extension) && !originalName.endsWith(extension)) {
            return originalName + extension;
        }
        return originalName;
    }

    /**
     * 从文件类型检查结果中获取识别的文件扩展名。
     */
    private String getRecognizedExtension(InvalidDataRecord record) {
        if (!StringUtils.hasText(record.getTypeCheckResult())) {
            return null;
        }
        try {
            FileTypeCheckResult result = MapperHolder.parseJson(record.getTypeCheckResult(), FileTypeCheckResult.class);
            if (result.getDetail() != null && StringUtils.hasText(result.getDetail().getExtension())) {
                return result.getDetail().getExtension();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * 执行 Groovy 路径解析脚本。
     * <p>脚本内置变量与 {@link GroovyRecordFilter} 一致。</p>
     *
     * @param executor 已编译的脚本执行器
     * @param record   当前失效数据记录
     * @return 脚本返回的 Map（path/name），若脚本返回非 Map 或 null 则返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> executePathScript(GroovyScriptExecutor executor, InvalidDataRecord record) {
        Object value = executor.run(InvalidDataGroovyScriptHelper.createBinding(record), PATH_SCRIPT_TIMEOUT_MILLIS);
        if (value instanceof Map) {
            return (Map<String, String>) value;
        }
        return null;
    }

    /**
     * 创建 Groovy 路径解析脚本执行器。
     *
     * @param script 脚本代码，为 null 或空时返回 null
     * @return 脚本执行器，脚本为空时返回 null
     */
    private GroovyScriptExecutor createPathScriptExecutor(String script) {
        return InvalidDataGroovyScriptHelper.createExecutor(script);
    }

    /** 路径解析脚本超时时间（毫秒） */
    private static final long PATH_SCRIPT_TIMEOUT_MILLIS = 5_000L;

    /** 批量操作每批大小 */
    private static final int BATCH_SIZE = 200;
}
