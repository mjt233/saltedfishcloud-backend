package com.sfc.dm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfc.dm.constant.InvalidDataError;
import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.enums.InvalidDataType;
import com.sfc.dm.enums.ProcessMethod;
import com.sfc.dm.model.dto.BatchResult;
import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.dto.InvalidDataFilterResult;
import com.sfc.dm.model.dto.InvalidDataQuery;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.sfc.dm.repo.InvalidDataRecordRepo;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.dao.jpa.FileInfoRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
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

import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    @Autowired
    private GroovyRecordFilter groovyRecordFilter;
    @Autowired
    private CacheService cacheService;

    private static final String CACHE_KEY_PREFIX = "sfc:dm:invalidData:filter:";
    private static final int CACHE_TTL_MINUTES = 5;

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
        applyQueryFilter(wrapper, query);
        Sort sort = buildSort(query);
        return CommonPageInfo.of(repo.findAll(wrapper.build(), PageRequest.of(pageable.getPage(), pageable.getSize(), sort)));
    }

    /**
     * 创建脚本筛选，执行全量查询 + Groovy 筛选，缓存筛选后的 ID 列表供后续分页查询。
     *
     * @param query 查询参数（含 filterScript）
     * @return 筛选结果（filterId + matchedCount）
     */
    @Transactional(readOnly = true)
    public InvalidDataFilterResult createFilter(InvalidDataQuery query) {
        if (query.getFilterScript() == null || query.getFilterScript().isBlank()) {
            throw new IllegalArgumentException("筛选脚本不能为空");
        }

        JpaLambdaQueryWrapper<InvalidDataRecord> wrapper = JpaLambdaQueryWrapper.get(InvalidDataRecord.class);
        applyQueryFilter(wrapper, query);
        applySort(wrapper, query);

        List<Long> filteredIds;
        try (Stream<InvalidDataRecord> stream = repo.streamAll(wrapper.build())) {
            filteredIds = groovyRecordFilter.filter(stream, query.getFilterScript());
        }

        String filterId = UUID.randomUUID().toString();
        cacheService.set(CACHE_KEY_PREFIX + filterId, filteredIds, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        InvalidDataFilterResult result = new InvalidDataFilterResult();
        result.setFilterId(filterId);
        result.setMatchedCount(filteredIds.size());
        return result;
    }

    /**
     * 流式查询可认领的失效数据记录（UNIQUE 模式下类型为失效物理存储）。
     * <p>在 {@link #list(InvalidDataQuery, PageableRequest)} 的筛选条件基础上，
     * 强制限定 type = PHYSICAL_STORAGE 且 storeMode = UNIQUE。</p>
     *
     * @param query 失效数据筛选条件
     * @return 可认领记录的流，调用方负责关闭
     */
    public Stream<InvalidDataRecord> streamClaimableRecords(InvalidDataQuery query) {
        JpaLambdaQueryWrapper<InvalidDataRecord> wrapper = JpaLambdaQueryWrapper.get(InvalidDataRecord.class);
        applyQueryFilter(wrapper, query);
        wrapper.eq(InvalidDataRecord::getType, InvalidDataType.PHYSICAL_STORAGE)
                .eq(InvalidDataRecord::getStoreMode, StoreMode.UNIQUE);
        applySort(wrapper, query);
        return repo.streamAll(wrapper.build());
    }

    /**
     * 根据 filterId 从缓存中获取筛选结果并分页返回。
     *
     * @param filterId 筛选缓存ID
     * @param pageable 分页参数
     * @return 分页结果
     */
    public CommonPageInfo<InvalidDataRecord> listByFilterId(String filterId, PageableRequest pageable) {
        List<Long> allIds = cacheService.get(CACHE_KEY_PREFIX + filterId);
        if (allIds == null) {
            throw new JsonException(InvalidDataError.FILTER_EXPIRED);
        }

        int page = pageable.getPage();
        int size = pageable.getSize();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allIds.size());
        long totalPage = (allIds.size() + size - 1) / size;

        if (fromIndex >= allIds.size()) {
            return new CommonPageInfo<InvalidDataRecord>()
                    .setContent(List.of())
                    .setTotalCount(allIds.size())
                    .setTotalPage(totalPage);
        }

        List<Long> pageIds = allIds.subList(fromIndex, toIndex);
        List<InvalidDataRecord> records = repo.findByIds(pageIds);

        Map<Long, InvalidDataRecord> recordMap = records.stream()
                .collect(Collectors.toMap(InvalidDataRecord::getId, Function.identity()));
        List<InvalidDataRecord> ordered = pageIds.stream()
                .map(recordMap::get)
                .collect(Collectors.toList());

        return new CommonPageInfo<InvalidDataRecord>()
                .setContent(ordered)
                .setTotalCount(allIds.size())
                .setTotalPage(totalPage);
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
     * 批量丢弃。
     * <p>优化策略（针对 ids 数量较大的场景）：</p>
     * <ul>
     *     <li>批量查询：通过 {@link InvalidDataRecordRepo#findAllById} 一次性加载所有记录，
     *     以 1 次查询替代 N 次 {@link InvalidDataRecordRepo#findById}，在内存中校验状态。</li>
     *     <li>按类型分流：
     *         <ul>
     *             <li>{@link InvalidDataType#FILE_RECORD}：无 IO，直接批量更新状态与处理方式。</li>
     *             <li>{@link InvalidDataType#PHYSICAL_STORAGE}：需删除物理文件（IO 不可避免，逐个执行），
     *             删除成功后再批量更新状态与处理方式；删除失败的记录保持 PENDING 并计入错误。</li>
     *         </ul>
     *     </li>
     *     <li>批量更新：通过 {@link InvalidDataRecordRepo#updateProcessResultByIds} 分批
     *     （每批 {@value #BATCH_SIZE} 条）执行 UPDATE，替代逐条 save。</li>
     * </ul>
     * <p>注意：本方法不加 {@code @Transactional}，避免物理 IO 期间长事务持锁；
     * 每个批量 UPDATE 自带事务，删除失败回滚后对应记录保持 PENDING 状态。</p>
     *
     * @param ids 待丢弃的失效数据记录ID列表
     * @return 批量操作结果
     */
    public BatchResult discard(List<Long> ids) {
        // 1. 批量查询：1 次 SELECT 替代 N 次 findById
        List<InvalidDataRecord> records = repo.findAllById(ids);
        Map<Long, InvalidDataRecord> recordMap = records.stream()
                .collect(Collectors.toMap(InvalidDataRecord::getId, Function.identity()));

        // 2. 内存校验状态，按类型分流有效记录
        List<Long> fileRecordIds = new ArrayList<>();
        List<Long> physicalStorageIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            InvalidDataRecord record = recordMap.get(id);
            if (record == null) {
                errors.add("ID " + id + ": 记录不存在");
            } else if (record.getStatus() != InvalidDataStatus.PENDING) {
                errors.add("ID " + id + ": 当前状态不允许丢弃");
            } else if (record.getType() == InvalidDataType.PHYSICAL_STORAGE) {
                physicalStorageIds.add(id);
            } else {
                fileRecordIds.add(id);
            }
        }

        int success = 0;

        // 3. FILE_RECORD：无 IO，直接批量更新状态与处理方式
        if (!fileRecordIds.isEmpty()) {
            success += batchUpdateProcessResult(fileRecordIds);
        }

        // 4. PHYSICAL_STORAGE：逐个删除物理文件（IO），成功后再批量更新状态
        if (!physicalStorageIds.isEmpty()) {
            List<Long> deletedIds = new ArrayList<>(physicalStorageIds.size());
            Storage storage = storeServiceFactory.getService().getStorageProvider();
            for (Long id : physicalStorageIds) {
                InvalidDataRecord record = recordMap.get(id);
                try {
                    storage.delete(record.getStoragePath());
                    deletedIds.add(id);
                } catch (Exception e) {
                    errors.add("ID " + id + ": 删除物理存储失败: " + e.getMessage());
                }
            }
            if (!deletedIds.isEmpty()) {
                success += batchUpdateProcessResult(deletedIds);
            }
        }

        return new BatchResult(success, errors.size(), errors);
    }

    /**
     * 分批更新记录的处理结果为 COMPLETED + DISCARD，返回累计更新行数。
     *
     * @param ids 待更新的记录ID列表
     * @return 实际更新的记录数
     */
    private int batchUpdateProcessResult(List<Long> ids) {
        int updated = 0;
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<Long> batch = ids.subList(i, Math.min(i + BATCH_SIZE, ids.size()));
            updated += repo.updateProcessResultByIds(batch, InvalidDataStatus.COMPLETED, ProcessMethod.DISCARD);
        }
        return updated;
    }

    /**
     * 丢弃所有可丢弃的数据
     */
    public BatchResult discardAll() {
        List<Long> ids = repo.findIdsByStatus(InvalidDataStatus.PENDING);
        return discard(ids);
    }

    /**
     * 按条件批量丢弃。
     * <p>根据 InvalidDataQuery 查询条件（支持 Groovy 脚本过滤）筛选出 PENDING 状态的记录，
     * 执行批量丢弃操作。状态强制为 PENDING，客户端传入的 status 字段会被忽略。</p>
     *
     * @param query 查询条件
     * @return 批量操作结果
     */
    public BatchResult discardByQuery(InvalidDataQuery query) {
        List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PENDING);
        return discard(ids);
    }

    /**
     * 按条件批量发布为可认领。
     * <p>根据 InvalidDataQuery 查询条件（支持 Groovy 脚本过滤）筛选出 PENDING 状态的记录，
     * 执行批量发布操作。状态强制为 PENDING，客户端传入的 status 字段会被忽略。</p>
     *
     * @param query 查询条件
     * @return 批量操作结果
     */
    public BatchResult publishByQuery(InvalidDataQuery query) {
        List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PENDING);
        return publish(ids);
    }

    /**
     * 按条件批量取消发布。
     * <p>根据 InvalidDataQuery 查询条件（支持 Groovy 脚本过滤）筛选出 PUBLISHED 状态的记录，
     * 执行批量取消发布操作。状态强制为 PUBLISHED，客户端传入的 status 字段会被忽略。</p>
     *
     * @param query 查询条件
     * @return 批量操作结果
     */
    public BatchResult unpublishByQuery(InvalidDataQuery query) {
        List<Long> ids = findIdsByQuery(query, InvalidDataStatus.PUBLISHED);
        return unpublish(ids);
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
     * 按条件查询匹配的记录ID列表。
     * 支持 DB 级别筛选条件和可选的 Groovy 脚本过滤。
     * <p>当 query 包含 filterScript 时，先通过 DB 条件流式查询，
     * 再经 Groovy 脚本过滤返回匹配的 ID 列表；否则直接流式提取 ID。</p>
     *
     * @param query         查询条件
     * @param forcedStatus  强制注入的状态（覆盖 query.status）
     * @return 匹配的记录ID列表
     */
    private List<Long> findIdsByQuery(InvalidDataQuery query, InvalidDataStatus forcedStatus) {
        query.setStatus(List.of(forcedStatus.name()));
        JpaLambdaQueryWrapper<InvalidDataRecord> wrapper = JpaLambdaQueryWrapper.get(InvalidDataRecord.class);
        applyQueryFilter(wrapper, query);
        if (query.getFilterScript() != null && !query.getFilterScript().isBlank()) {
            try (Stream<InvalidDataRecord> stream = repo.streamAll(wrapper.build())) {
                return groovyRecordFilter.filter(stream, query.getFilterScript());
            }
        } else {
            try (Stream<InvalidDataRecord> stream = repo.streamAll(wrapper.build())) {
                return stream.map(InvalidDataRecord::getId).collect(Collectors.toList());
            }
        }
    }

    /**
     * 向查询包装器应用 InvalidDataQuery 中的 5 个通用筛选条件。
     */
    private void applyQueryFilter(JpaLambdaQueryWrapper<InvalidDataRecord> wrapper, InvalidDataQuery query) {
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
    }

    /**
     * 向查询包装器应用排序（仅允许 fileSize、lastModified 字段）。
     */
    private void applySort(JpaLambdaQueryWrapper<InvalidDataRecord> wrapper, InvalidDataQuery query) {
        if (query.getSortBy() == null || query.getSortBy().isBlank()) {
            return;
        }
        if (!"fileSize".equals(query.getSortBy()) && !"lastModified".equals(query.getSortBy())) {
            throw new IllegalArgumentException("不支持的排序字段: " + query.getSortBy());
        }
        JpaLambdaQueryWrapper.SortType type = "ASC".equalsIgnoreCase(query.getSortOrder())
                ? JpaLambdaQueryWrapper.SortType.ASC
                : JpaLambdaQueryWrapper.SortType.DESC;
        wrapper.orderBy(type, query.getSortBy());
    }

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
