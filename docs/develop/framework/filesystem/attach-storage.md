# 附属存储（AttachStorage）使用说明

本文面向开发手册，介绍附属存储（AttachStorage）的定位、架构和使用方式。

## 概述

附属存储（`AttachStorage`）提供了一种轻量级的文件存储抽象，适用于**不适合通过主文件系统管理的辅助数据**，例如：

- 缩略图缓存
- 用户头像
- 断点续传分片
- 视频字幕缓存
- 快速分享临时文件

所有附属存储文件被限制在 `sys.store.root/attach/<storageDomainId>` 目录下，即使调用方传入 `.` 或 `..` 也不允许越过该目录。

## 架构

| 组件                            | 说明                                                            |
|-------------------------------|---------------------------------------------------------------|
| `AttachStorage`               | 存储操作接口，提供 `getFile`、`saveFile`、`delete`、`listFiles` 等方法       |
| `AttachStorageManager`        | 存储域管理器，负责注册和获取存储域                                             |
| `DefaultAttachStorageManager` | 默认实现（Spring `@Component`），内部维护 `definitionMap` 和 `storageMap` |
| `DefaultAttachStorage`        | 默认存储实现（非 Spring Bean），由 `DefaultAttachStorageManager` 按需创建    |

- 接口定义：`sfc-api/.../service/file/store/attach/AttachStorage.java`
- 管理器接口：`sfc-api/.../service/file/store/attach/AttachStorageManager.java`
- 默认实现：`sfc-core/.../service/file/store/attach/DefaultAttachStorageManager.java`
- 存储实现：`sfc-core/.../service/file/store/attach/DefaultAttachStorage.java`

## 使用方式

### 1) 使用 `@AttachStorageInject` 注解（推荐）

在 Spring Bean 的 `AttachStorage` 类型字段上添加注解即可，不需要手动注册或调用 `getStorage`：

```java

@Service
public class ExampleService {

    @AttachStorageInject(value = "my_domain", name = "示例存储域")
    private AttachStorage myStorage;

    public void doSomething() throws IOException {
        myStorage.saveFile("test.txt", resource);
        Resource file = myStorage.getFile("test.txt").orElse(null);
    }
}
```

注解参数：

| 参数            | 必填 | 说明                    |
|---------------|----|-----------------------|
| `value`       | 是  | 存储域唯一标识               |
| `name`        | 否  | 显示名称，未指定时默认等于 `value` |
| `description` | 否  | 描述信息                  |

注入由 `AttachStorageInjectProcessor`（`BeanPostProcessor`）自动完成，它依赖 `AttachStorageManager` 进行注册和获取。

### 2) 编程式使用（不推荐）

```java

@Service
public class ExampleService {

    private AttachStorage myStorage;

    public ExampleService(AttachStorageManager attachStorageManager) {
        attachStorageManager.registerStorageDomain(
                AttachStorageDomainDefinition.builder()
                        .id("my_domain")
                        .name("示例存储域")
                        .build()
        );
        myStorage = attachStorageManager.getStorage("my_domain");
    }
}
```

## 存储域复用规则

`DefaultAttachStorageManager.registerStorageDomain()` 使用 `putIfAbsent` 确保每个域只注册一次。当参数与已有注册完全相同时，重复注册会被忽略。
**不同存储域之间完全隔离**，每个域拥有独立的根目录。

## 已注册存储域一览

| 存储域 ID                  | 名称        | 所属模块                    | 用途         |
|-------------------------|-----------|-------------------------|------------|
| `user_avatar`           | 用户头像      | `sfc-core`              | 用户头像文件     |
| `thumbnail`             | 缩略图缓存     | `sfc-core`              | 文件缩略图缓存    |
| `third_platform_avatar` | 第三方平台头像缓存 | `sfc-core`              | 第三方登录头像缓存  |
| `breakpoint`            | 断点续传      | `sfc-core`              | 断点续传任务分片缓存 |
| `quick_share`           | 快速分享      | `sfc-ext-quick-share`   | 快速分享临时文件   |
| `pxe-boot`              | PXE启动缓存   | `sfc-ext-pxe-boot`      | ISO资源提取缓存  |
| `ve`                    | 视频元数据     | `sfc-ext-video-enhance` | 视频字幕提取缓存   |

## 注册存储域的注意事项

- 存储域 ID 只能包含字母、数字、`.`、`-` 和 `_`。
- 不能使用 `.` 或 `..` 作为存储域 ID。
- 同一个存储域可以被多个服务复用（当注册参数完全相同时），例如 `third_platform_avatar` 被 `ThirdPartyPlatformManagerImpl` 和
  `AbstractThirdPartyPlatformHandler` 共用。
- 存储域根目录在注册时会自动创建。

## 路径安全

`DefaultAttachStorage` 对所有文件路径进行规范化处理：

- 自动统一路径分隔符为 `/`。
- 阻止 `..` 越过存储域根目录。
- 路径为空时视为访问存储域根目录，但在文件操作（如 `saveFile`）中路径不能为空。

## 代码参考

- `@AttachStorageInject` 注解：`sfc-api/.../service/file/store/attach/AttachStorageInject.java`
- 注解处理器：`sfc-core/.../service/file/store/attach/AttachStorageInjectProcessor.java`
- 管理器接口：`sfc-api/.../service/file/store/attach/AttachStorageManager.java`
- `DefaultAttachStorageManager`：`sfc-core/.../service/file/store/attach/DefaultAttachStorageManager.java`
- `DefaultAttachStorage`：`sfc-core/.../service/file/store/attach/DefaultAttachStorage.java`
