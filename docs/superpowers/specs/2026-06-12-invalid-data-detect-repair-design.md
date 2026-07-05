# 失效数据识别与修复 - 设计规格

## 概述

`sfc-ext-data-manager` 插件的失效数据识别与修复功能，包含失效数据检测、修复、认领、丢弃、文件类型识别。

## 实现范围

- 失效数据检测与修复（完整）
- 文件类型识别接口（FileTypeCheckProvider/Checker）
- 内置 Provider：仅 VideoCheckVideoProvider

## 数据模型

### 枚举

- `InvalidDataType`：FILE_RECORD / PHYSICAL_STORAGE
- `InvalidDataStatus`：PENDING / PUBLISHED / CLAIMED / COMPLETED
- `ProcessMethod`：DISCARD / CLAIM / AUTO_REPAIR
- `DataManagerTaskType`：INVALID_DATA_DETECT / FILE_TYPE_CHECK

### 实体类（继承 AuditModel）

**InvalidDataRecord**（invalid_data_record）：
- type, storagePath, ownerUid, diskPath, fileSize, lastModified
- needIdentify, fileType, metadata
- status, processMethod, md5

**ClaimRecord**（claim_record）：
- invalidDataId, targetUid, fileName, savePath
- uid 字段表示操作人/认领人

### 状态机

UNIQUE：`PENDING -> PUBLISHED -> CLAIMED -> COMPLETED`，PENDING可直接丢弃，PUBLISHED可取消发布
RAW：`PENDING -> COMPLETED(AUTO_REPAIR)` 或 `PENDING -> COMPLETED(DISCARD)`

## Service 层

### InvalidDataService

```java
CommonPageInfo<InvalidDataRecord> list(String status, Long ownerUid, Long minFileSize, Long maxFileSize, String fileType, Pageable pageable);
void publish(Long id);
void unpublish(Long id);
void quickFix(List<Long> ids);       // 批量修复（单个传1个元素）
void quickFixAll();                   // 修复所有待处理
void discard(List<Long> ids);         // 批量丢弃
void discardAll();                    // 丢弃所有可丢弃
void markCompleted(Long id);
InvalidDataRecord getDetail(Long id);
```

### ClaimService

```java
void claim(Long invalidDataId, ClaimParam param, Long operatorUid);
List<ClaimRecord> listByInvalidDataId(Long invalidDataId);
CommonPageInfo<ClaimRecord> listByUid(Long uid, Pageable pageable);
```

## Controller 层

`/api/dataManager/invalidData`：

| 端点 | 方法 | 权限 |
|------|------|------|
| `/detect` | POST | ADMIN |
| `/identify` | POST | ADMIN |
| `/list` | GET | ADMIN |
| `/detail/{id}` | GET | ADMIN |
| `/publish/{id}` | POST | ADMIN |
| `/unpublish/{id}` | POST | ADMIN |
| `/quickFix` | POST | ADMIN |
| `/quickFix/all` | POST | ADMIN |
| `/discard` | POST | ADMIN |
| `/discard/all` | POST | ADMIN |
| `/markCompleted/{id}` | POST | ADMIN |
| `/claim` | POST | 登录用户 |
| `/claims/{invalidDataId}` | GET | ADMIN |
| `/myClaims` | GET | 登录用户 |

## 异步任务

- `InvalidDataDetectTask`：清除PENDING记录 -> 扫描（忽略挂载文件记录is_mount=1） -> 保存结果
- `FileTypeCheckTask`：查询needIdentify记录 -> 识别 -> 更新fileType/metadata
- 并发控制：同时只能有1个检测或识别任务

## 文件类型识别

接口：FileTypeCheckProvider / FileTypeChecker / FileMetadataDefine / FileTypeCheckResult

非本地资源策略：下载到临时文件，Provider统一接收本地文件，识别完成后删除临时文件。

内置：VideoCheckProvider（mp4/avi/mvk等，提取时长/分辨率）
