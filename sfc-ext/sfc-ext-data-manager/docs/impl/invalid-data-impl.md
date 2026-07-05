# 失效数据识别与修复 - 实现计划

## 包结构

```
com.sfc.dm
├── config/
│   └── DataManagerAutoConfiguration.java   (已有，需完善)
├── constant/
│   └── DataManagerTaskType.java            (异步任务类型常量)
├── enums/
│   ├── InvalidDataType.java                (失效数据类型枚举)
│   ├── InvalidDataStatus.java              (失效数据状态枚举)
│   └── ProcessMethod.java                  (处理方式枚举)
├── model/
│   ├── po/
│   │   ├── InvalidDataRecord.java          (失效数据记录实体)
│   │   └── ClaimRecord.java                (认领记录实体)
│   └── dto/
│       ├── InvalidDataQuery.java           (查询参数)
│       ├── ClaimParam.java                 (认领请求参数)
│       └── InvalidDataStat.java            (统计信息，预留)
├── repo/
│   ├── InvalidDataRecordRepo.java
│   └── ClaimRecordRepo.java
├── service/
│   ├── InvalidDataService.java
│   └── ClaimService.java
├── controller/
│   └── InvalidDataController.java
└── task/
    ├── InvalidDataDetectTask.java          (检测异步任务)
    ├── InvalidDataDetectTaskFactory.java
    ├── FileTypeCheckTask.java              (文件识别异步任务)
    └── FileTypeCheckTaskFactory.java
```

---

## 一、枚举与常量

### 1.1 InvalidDataType - 失效数据类型

```java
public enum InvalidDataType {
    FILE_RECORD,    // 失效文件记录
    PHYSICAL_STORAGE // 失效物理存储
}
```

### 1.2 InvalidDataStatus - 失效数据状态

```java
public enum InvalidDataStatus {
    PENDING,        // 待处理
    PUBLISHED,      // 已发布可认领（仅UNIQUE模式）
    CLAIMED,        // 已认领（仅UNIQUE模式）
    COMPLETED       // 处理完成
}
```

### 1.3 ProcessMethod - 处理方式

```java
public enum ProcessMethod {
    DISCARD,        // 丢弃
    CLAIM,          // 认领
    AUTO_REPAIR     // 自动修复
}
```

### 1.4 DataManagerTaskType - 异步任务类型常量

```java
public interface DataManagerTaskType {
    String INVALID_DATA_DETECT = "invalid-data-detect";
    String FILE_TYPE_CHECK = "file-type-check";
}
```

---

## 二、实体设计

### 2.1 InvalidDataRecord - 失效数据记录

表名: `invalid_data_record`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键（雪花ID，继承AuditModel） |
| uid | Long | 操作人ID（继承AuditModel） |
| create_at | Date | 创建时间（继承AuditModel） |
| update_at | Date | 更新时间（继承AuditModel） |
| type | String | 失效数据类型：FILE_RECORD / PHYSICAL_STORAGE |
| storage_path | String | 完整物理存储路径 |
| owner_uid | Long | 所属用户id（公共网盘为0） |
| disk_path | String | 网盘路径（UNIQUE模式下为null） |
| file_size | Long | 文件大小 |
| last_modified | Date | 最后修改时间 |
| need_identify | Boolean | 是否为待识别文件 |
| file_type | String | 文件类型typeId（识别后填充） |
| metadata | String | 元数据JSON（识别后填充） |
| status | String | 状态：PENDING/PUBLISHED/CLAIMED/COMPLETED |
| process_method | String | 处理方式（仅COMPLETED状态有值） |
| md5 | String | 文件MD5（用于引用检查和查找） |

索引:
- `idx_invalid_data_record_status` (status)
- `idx_invalid_data_record_owner_uid` (owner_uid)
- `idx_invalid_data_record_md5` (md5)

关键设计点:
- 继承 `AuditModel`，`uid` 字段表示操作人（管理员）ID，`owner_uid` 表示失效数据所属用户ID
- `disk_path` 在UNIQUE模式下为null，RAW模式下为实际网盘路径
- `metadata` 存储JSON格式的元数据（如 `{"width":"1024","height":"768"}`）

### 2.2 ClaimRecord - 认领记录

表名: `claim_record`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键（雪花ID，继承AuditModel） |
| uid | Long | 操作人ID/认领人ID（继承AuditModel） |
| create_at | Date | 创建时间（继承AuditModel） |
| update_at | Date | 更新时间（继承AuditModel） |
| invalid_data_id | Long | 关联的失效数据记录ID |
| target_uid | Long | 保存的目标网盘id（0=公共网盘） |
| file_name | String | 认领时填写的文件名 |
| save_path | String | 认领时填写的保存路径 |

索引:
- `idx_claim_record_invalid_data_id` (invalid_data_id)
- `idx_claim_record_uid` (uid)

---

## 三、Repository 层

### 3.1 InvalidDataRecordRepo

```java
public interface InvalidDataRecordRepo extends BaseRepo<InvalidDataRecord> {
    // 按状态查询
    Page<InvalidDataRecord> findByStatus(String status, Pageable pageable);

    // 按状态和ownerUid查询
    Page<InvalidDataRecord> findByStatusAndOwnerUid(String status, Long ownerUid, Pageable pageable);

    // 按状态批量删除（用于检测开始时清理待处理记录）
    @Transactional @Modifying
    @Query("DELETE FROM InvalidDataRecord t WHERE t.status = :status")
    int deleteByStatus(@Param("status") String status);

    // 按MD5查询（用于检查UNIQUE模式引用计数）
    @Query("SELECT COUNT(t) > 0 FROM InvalidDataRecord t WHERE t.md5 = :md5 AND t.status != 'COMPLETED'")
    boolean existsActiveByMd5(@Param("md5") String md5);

    // 查询所有待识别的记录
    Page<InvalidDataRecord> findByStatusAndNeedIdentifyTrue(String status, Pageable pageable);
}
```

### 3.2 ClaimRecordRepo

```java
public interface ClaimRecordRepo extends BaseRepo<ClaimRecord> {
    // 按失效数据ID查询所有认领记录
    List<ClaimRecord> findByInvalidDataId(Long invalidDataId);

    // 按失效数据ID查询认领数量
    @Query("SELECT COUNT(t) FROM ClaimRecord t WHERE t.invalidDataId = :invalidDataId")
    long countByInvalidDataId(@Param("invalidDataId") Long invalidDataId);

    // 按用户ID查询（用户查看自己的认领记录）
    Page<ClaimRecord> findByUid(Long uid, Pageable pageable);
}
```

---

## 四、Service 层

### 4.1 InvalidDataService

核心业务服务，处理失效数据的CRUD和状态转换。

**依赖注入:**
- `InvalidDataRecordRepo`
- `ClaimRecordRepo`
- `FileInfoRepo`（用于MD5引用计数检查）
- `FileSystemMetadataOperator`（用于文件记录操作）
- `StoreServiceFactory`（用于物理存储操作）
- `SysCommonConfig`（获取存储模式）

**主要方法:**

```java
// 查询失效数据列表（分页）
CommonPageInfo<InvalidDataRecord> list(String status, Long ownerUid, Pageable pageable);

// 发布为可认领（UNIQUE模式，PENDING -> PUBLISHED）
void publish(Long id);

// 取消发布（UNIQUE模式，PUBLISHED -> PENDING）
// 前置条件：无认领记录
void unpublish(Long id);

// 快速修复（RAW模式，PENDING -> COMPLETED(AUTO_REPAIR)）
// 失效文件记录：检查物理存储存在且原路径可用 -> 重建文件记录
// 失效文件记录：物理存储不存在 -> 通过md5查找并复制
// 失效物理存储：直接重建文件记录
void quickFix(Long id);

// 丢弃（UNIQUE: PENDING -> COMPLETED(DISCARD)，RAW: PENDING -> COMPLETED(DISCARD)）
// UNIQUE前置条件：MD5引用计数=0且状态不为CLAIMED
// RAW前置条件：状态为PENDING
void discard(Long id);

// 标记处理完成（UNIQUE模式，CLAIMED -> COMPLETED(CLAIM)）
// 前置条件：状态为CLAIMED
void markCompleted(Long id);

// 获取失效数据详情
InvalidDataRecord getDetail(Long id);

// 统计信息（预留）
Map<String, Object> getStatistics();
```

**quickFix 核心逻辑（RAW模式）:**

```java
private void quickFixFileRecord(InvalidDataRecord record) {
    // 1. 检查原路径是否可用
    if (metadataOperator.exist(record.getOwnerUid(), record.getDiskPath())) {
        // 路径被占用，修复失败
        throw new JsonException("修复失败：原路径已被占用");
    }

    // 2. 检查物理存储是否存在
    Storage storage = storeServiceFactory.getService().getStorageProvider();
    if (storage.exist(record.getStoragePath())) {
        // 物理存储存在，直接重建文件记录
        FileInfo fileInfo = new FileInfo();
        fileInfo.setMd5(record.getMd5());
        fileInfo.setSize(record.getFileSize());
        fileInfo.setName(extractFileName(record.getDiskPath()));
        metadataOperator.saveRecord(fileInfo, record.getDiskPath());
    } else {
        // 物理存储不存在，通过md5查找并复制
        List<FileInfo> sameMd5Files = metadataOperator.getFileInfoByMd5(record.getMd5(), 1);
        if (sameMd5Files.isEmpty()) {
            throw new JsonException("修复失败：物理存储不存在且无相同MD5文件");
        }
        // 复制物理文件到原路径
        FileInfo source = sameMd5Files.get(0);
        // ... 复制逻辑
    }

    // 3. 标记处理完成
    record.setStatus(InvalidDataStatus.COMPLETED.name());
    record.setProcessMethod(ProcessMethod.AUTO_REPAIR.name());
    repo.save(record);
}
```

**discard 核心逻辑:**

```java
private void discardPhysicalStorage(InvalidDataRecord record) {
    // 1. UNIQUE模式检查MD5引用计数
    if (storeMode == StoreMode.UNIQUE) {
        // 检查是否已被认领
        if (InvalidDataStatus.CLAIMED.name().equals(record.getStatus())) {
            throw new JsonException("已认领的数据不允许丢弃");
        }
        // 实时检查文件记录引用
        List<FileInfo> refs = metadataOperator.getFileInfoByMd5(record.getMd5(), 1);
        if (!refs.isEmpty()) {
            throw new JsonException("该文件仍被其他文件记录引用，不允许丢弃");
        }
        // 删除物理存储
        storage.delete(record.getStoragePath());
    }

    // 2. 标记处理完成
    record.setStatus(InvalidDataStatus.COMPLETED.name());
    record.setProcessMethod(ProcessMethod.DISCARD.name());
    repo.save(record);
}
```

### 4.2 ClaimService

处理认领相关业务（仅UNIQUE模式）。

**依赖注入:**
- `ClaimRecordRepo`
- `InvalidDataRecordRepo`
- `FileSystemMetadataOperator`
- `FileInfoRepo`

**主要方法:**

```java
// 认领
// 权限校验：UIDValidator
// 业务校验：状态为PUBLISHED或CLAIMED，文件名冲突检查
// 操作：创建文件记录，创建认领记录，更新失效数据状态为CLAIMED
void claim(Long invalidDataId, ClaimParam param, Long operatorUid);

// 查询某条失效数据的所有认领记录
List<ClaimRecord> listByInvalidDataId(Long invalidDataId);

// 查询用户的认领记录
CommonPageInfo<ClaimRecord> listByUid(Long uid, Pageable pageable);
```

**claim 核心逻辑:**

```java
public void claim(Long invalidDataId, ClaimParam param, Long operatorUid) {
    // 1. 查询失效数据记录
    InvalidDataRecord record = invalidDataRepo.findById(invalidDataId)
        .orElseThrow(() -> new JsonException("失效数据记录不存在"));

    // 2. 状态校验
    if (!PUBLISHED.equals(record.getStatus()) && !CLAIMED.equals(record.getStatus())) {
        throw new JsonException("当前状态不允许认领");
    }

    // 3. 权限校验
    Long targetUid = param.getTargetUid();
    if (!PUBLISHED.equals(record.getStatus())) {
        // 未发布的数据仅管理员可操作
        UIDValidator.validate(operatorUid, true);
    }
    if (targetUid == 0) {
        // 公共网盘仅管理员可操作
        UIDValidator.validate(operatorUid, true);
    } else if (!targetUid.equals(operatorUid)) {
        // 非自己的网盘仅管理员可操作
        UIDValidator.validate(operatorUid, true);
    }

    // 4. 文件名冲突检查
    String diskPath = buildDiskPath(targetUid, param.getSavePath(), param.getFileName());
    if (metadataOperator.exist(targetUid, diskPath)) {
        throw new JsonException("文件名已存在，请修改后重试");
    }

    // 5. 创建文件记录
    FileInfo fileInfo = new FileInfo();
    fileInfo.setMd5(record.getMd5());
    fileInfo.setSize(record.getFileSize());
    fileInfo.setName(param.getFileName());
    metadataOperator.saveRecord(fileInfo, diskPath);

    // 6. 创建认领记录
    ClaimRecord claimRecord = new ClaimRecord();
    claimRecord.setUid(operatorUid);
    claimRecord.setInvalidDataId(invalidDataId);
    claimRecord.setTargetUid(targetUid);
    claimRecord.setFileName(param.getFileName());
    claimRecord.setSavePath(param.getSavePath());
    claimRecordRepo.save(claimRecord);

    // 7. 更新失效数据状态
    record.setStatus(InvalidDataStatus.CLAIMED.name());
    invalidDataRepo.save(record);
}
```

---

## 五、Controller 层

### 5.1 InvalidDataController

路径前缀: `/api/dataManager/invalidData`

```java
@Slf4j
@RestController
@RequestMapping("/api/dataManager/invalidData")
public class InvalidDataController {

    @Autowired
    private InvalidDataService invalidDataService;
    @Autowired
    private ClaimService claimService;
    @Autowired
    private AsyncTaskManager asyncTaskManager;

    // === 管理员操作 ===

    /** 发起失效数据检测（异步任务） */
    @PostMapping("detect")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> detect() {
        // 并发检查：是否有进行中的检测或识别任务
        // 创建异步任务
    }

    /** 发起文件识别（异步任务） */
    @PostMapping("identify")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> identify() {
        // 并发检查
        // 创建异步任务
    }

    /** 查询失效数据列表（分页） */
    @GetMapping("list")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<CommonPageInfo<InvalidDataRecord>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerUid,
            Pageable pageable) {
        return JsonResultImpl.getInstance(invalidDataService.list(status, ownerUid, pageable));
    }

    /** 获取失效数据详情 */
    @GetMapping("detail/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<InvalidDataRecord> detail(@PathVariable Long id) {
        return JsonResultImpl.getInstance(invalidDataService.getDetail(id));
    }

    /** 发布为可认领（UNIQUE模式） */
    @PostMapping("publish/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> publish(@PathVariable Long id) {
        invalidDataService.publish(id);
        return JsonResult.emptySuccess();
    }

    /** 取消发布（UNIQUE模式） */
    @PostMapping("unpublish/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> unpublish(@PathVariable Long id) {
        invalidDataService.unpublish(id);
        return JsonResult.emptySuccess();
    }

    /** 快速修复（RAW模式） */
    @PostMapping("quickFix/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> quickFix(@PathVariable Long id) {
        invalidDataService.quickFix(id);
        return JsonResult.emptySuccess();
    }

    /** 丢弃 */
    @PostMapping("discard/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> discard(@PathVariable Long id) {
        invalidDataService.discard(id);
        return JsonResult.emptySuccess();
    }

    /** 标记处理完成（UNIQUE模式） */
    @PostMapping("markCompleted/{id}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<?> markCompleted(@PathVariable Long id) {
        invalidDataService.markCompleted(id);
        return JsonResult.emptySuccess();
    }

    // === 认领操作（普通用户+管理员） ===

    /** 认领失效数据（UNIQUE模式） */
    @PostMapping("claim")
    public JsonResult<?> claim(@RequestBody @Validated ClaimParam param, @UID Long uid) {
        claimService.claim(param.getInvalidDataId(), param, uid);
        return JsonResult.emptySuccess();
    }

    /** 查看某条失效数据的认领记录 */
    @GetMapping("claims/{invalidDataId}")
    @RolesAllowed(SysRole.ADMIN)
    public JsonResult<List<ClaimRecord>> claims(@PathVariable Long invalidDataId) {
        return JsonResultImpl.getInstance(claimService.listByInvalidDataId(invalidDataId));
    }

    /** 查看我的认领记录 */
    @GetMapping("myClaims")
    public JsonResult<CommonPageInfo<ClaimRecord>> myClaims(@UID Long uid, Pageable pageable) {
        return JsonResultImpl.getInstance(claimService.listByUid(uid, pageable));
    }
}
```

---

## 六、异步任务

### 6.1 InvalidDataDetectTaskFactory

```java
@Component
public class InvalidDataDetectTaskFactory implements AsyncTaskFactory {
    @Autowired private InvalidDataRecordRepo invalidDataRepo;
    @Autowired private StoreServiceFactory storeServiceFactory;
    @Autowired private FileSystemMetadataOperator metadataOperator;
    @Autowired private SysCommonConfig sysCommonConfig;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        InvalidDataDetectTask task = new InvalidDataDetectTask();
        task.setInvalidDataRepo(invalidDataRepo);
        task.setStoreServiceFactory(storeServiceFactory);
        task.setMetadataOperator(metadataOperator);
        task.setSysCommonConfig(sysCommonConfig);
        return task;
    }

    @Override
    public String getTaskType() {
        return DataManagerTaskType.INVALID_DATA_DETECT;
    }
}
```

### 6.2 InvalidDataDetectTask

核心执行逻辑：

```java
public class InvalidDataDetectTask implements AsyncTask {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private final ProgressRecord progressRecord = new ProgressRecord();

    @Override
    public void execute(OutputStream logOutputStream) {
        running.set(true);
        PrintWriter log = new PrintWriter(logOutputStream);
        try {
            // 1. 清除上一轮待处理记录
            log.println("清除上一轮待处理的检测结果...");
            invalidDataRepo.deleteByStatus(InvalidDataStatus.PENDING.name());

            // 2. 根据存储模式执行扫描
            StoreMode mode = sysCommonConfig.getStoreMode();
            List<InvalidDataRecord> results;

            if (mode == StoreMode.RAW) {
                results = scanRawMode(log);
            } else {
                results = scanUniqueMode(log);
            }

            // 3. 批量保存检测结果
            progressRecord.setTotal(results.size()).setLoaded(0);
            for (int i = 0; i < results.size(); i++) {
                if (interrupted.get()) break;
                invalidDataRepo.save(results.get(i));
                progressRecord.setLoaded(i + 1);
                log.printf("保存检测结果 [%d/%d]%n", i + 1, results.size());
            }

            log.printf("检测完成，共发现 %d 条失效数据%n", results.size());
        } catch (Exception e) {
            log.println("检测异常: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            running.set(false);
            log.flush();
        }
    }

    private List<InvalidDataRecord> scanRawMode(PrintWriter log) {
        // RAW模式扫描逻辑：
        // 1. 扫描公共网盘和所有用户网盘的物理存储
        // 2. 与文件记录比对
        // 3. 同时扫描文件记录，检查物理存储是否存在
    }

    private List<InvalidDataRecord> scanUniqueMode(PrintWriter log) {
        // UNIQUE模式扫描逻辑：
        // 1. 扫描storeRoot下所有无拓展名文件（物理存储）
        // 2. 获取所有文件记录的MD5集合
        // 3. 比对找出无对应记录的物理存储
    }
}
```

### 6.3 FileTypeCheckTaskFactory

```java
@Component
public class FileTypeCheckTaskFactory implements AsyncTaskFactory {
    @Autowired private InvalidDataRecordRepo invalidDataRepo;
    @Autowired private StoreServiceFactory storeServiceFactory;
    @Autowired private List<FileTypeCheckProvider> providers;

    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        FileTypeCheckTask task = new FileTypeCheckTask();
        task.setInvalidDataRepo(invalidDataRepo);
        task.setStoreServiceFactory(storeServiceFactory);
        task.setProviders(providers);
        return task;
    }

    @Override
    public String getTaskType() {
        return DataManagerTaskType.FILE_TYPE_CHECK;
    }
}
```

### 6.4 FileTypeCheckTask

```java
public class FileTypeCheckTask implements AsyncTask {
    @Override
    public void execute(OutputStream logOutputStream) {
        // 1. 查询所有 needIdentify=true 且 status=PENDING 的记录
        // 2. 遍历每条记录：
        //    a. 通过 Storage.getResource() 获取文件资源
        //    b. 遍历已注册的 FileTypeCheckProvider 进行识别
        //    c. 更新记录的 fileType 和 metadata 字段
        // 3. 更新进度
    }
}
```

---

## 七、文件类型识别接口

### 7.1 FileTypeCheckProvider

```java
public interface FileTypeCheckProvider {
    String getId();
    String getTypeName();
    String getTypeId();
    List<String> getSupportedFileExtensions();
    List<FileMetadataDefine> getMetadataDefines();
    FileTypeCheckResultDetail checkFile(Resource file, boolean extraMetadata);
}
```

### 7.2 FileTypeChecker

```java
public interface FileTypeChecker {
    void addProvider(FileTypeCheckProvider provider);
    List<FileTypeCheckProvider> getProviders();
    FileTypeCheckResult checkFile(Resource file, boolean extraMetadata);
}
```

### 7.3 FileTypeCheckerImpl

```java
@Component
public class FileTypeCheckerImpl implements FileTypeChecker {
    private final List<FileTypeCheckProvider> providers = new ArrayList<>();

    @Override
    public void addProvider(FileTypeCheckProvider provider) {
        providers.add(provider);
    }

    @Override
    public FileTypeCheckResult checkFile(Resource file, boolean extraMetadata) {
        for (FileTypeCheckProvider provider : providers) {
            FileTypeCheckResultDetail detail = provider.checkFile(file, extraMetadata);
            if (detail != null) {
                FileTypeCheckResult result = new FileTypeCheckResult();
                result.setProviderId(provider.getId());
                result.setTypeId(provider.getTypeId());
                result.setTypeName(provider.getTypeName());
                result.setDetail(detail);
                return result;
            }
        }
        return null;
    }
}
```

### 7.4 内置Provider（本次实现）

本次实现以下Provider：
- `ImageCheckProvider` - 图片类型识别
- `VideoCheckProvider` - 视频类型识别
- `AudioCheckProvider` - 音频类型识别
- `DocumentCheckProvider` - 文档类型识别（PDF/Office）
- `ArchiveCheckProvider` - 压缩文件识别
- `TextCheckProvider` - 文本文件识别

每个Provider通过 `getSupportedFileExtensions()` 进行快速文件名匹配，通过 `checkFile()` 读取文件头进行实际识别。

---

## 八、AutoConfiguration 完善

```java
@Configuration
@ComponentScan(basePackageClasses = DataManagerAutoConfiguration.class)
@EntityScan(basePackageClasses = InvalidDataRecord.class)
@EnableJpaRepositories(basePackageClasses = InvalidDataRecordRepo.class)
public class DataManagerAutoConfiguration {
    @Bean
    public InvalidDataController invalidDataController() {
        return new InvalidDataController();
    }

    @Bean
    public InvalidDataService invalidDataService() {
        return new InvalidDataService();
    }

    @Bean
    public ClaimService claimService() {
        return new ClaimService();
    }

    @Bean
    public FileTypeChecker fileTypeChecker() {
        return new FileTypeCheckerImpl();
    }
}
```

---

## 九、实现顺序

### Phase 1: 基础层
1. 枚举类（InvalidDataType, InvalidDataStatus, ProcessMethod）
2. 常量类（DataManagerTaskType）
3. 实体类（InvalidDataRecord, ClaimRecord）
4. Repository接口（InvalidDataRecordRepo, ClaimRecordRepo）

### Phase 2: Service 层
5. InvalidDataService（核心业务逻辑：查询、发布、快速修复、丢弃、标记完成）
6. ClaimService（认领业务逻辑：权限校验、文件名冲突检查、创建记录）

### Phase 3: 文件识别
7. FileTypeCheckProvider 接口和相关DTO
8. FileTypeChecker 接口和实现
9. 内置Provider实现（Image, Video, Audio, Document, Archive, Text）

### Phase 4: 异步任务
10. InvalidDataDetectTaskFactory + InvalidDataDetectTask
11. FileTypeCheckTaskFactory + FileTypeCheckTask

### Phase 5: Controller 层
12. InvalidDataController（所有API端点）

### Phase 6: 集成
13. 完善 DataManagerAutoConfiguration
14. 编译验证
15. get_file_problems 检查
