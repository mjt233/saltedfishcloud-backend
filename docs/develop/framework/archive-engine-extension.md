# 压缩引擎扩展指南

本文介绍如何为 `sfc-archive` 模块新增一种压缩格式支持。

## 1. 核心接口说明

统一接口位于 `sfc-archive-api`：


- `ArchiveEngineProvider`：引擎提供者，负责声明能力和创建执行器。
- `ArchiveEngineCompressor`：压缩任务执行器。
- `ArchiveEngineDecompressor`：解压任务执行器。
- `ArchiveManager`：统一入口，负责注册、查询和创建引擎。

数据模型：

- `ArchiveResource`：压缩资源/压缩包资源描述。
- `ArchiveProperty`：任务属性（压缩级别、加密参数、编码、回调）。
- `EncryptionParam`：加密参数。
- `CompressionLevel`：统一压缩级别枚举。

## 2. 新增一个引擎 Provider

建议继承 `com.sfc.archive.engine.AbstractArchiveEngineProvider`，只实现差异化能力：

1. 定义唯一 `getId()` 和可读的 `getName()`。
2. 返回支持的扩展名：  
    - `getSupportedCompressExtensions()`
    - `getSupportedDecompressExtensions()`

3. 在 `createCompressor`/`createDecompressor` 中创建具体执行器。
4. 如不支持某项能力，抛出业务异常。

> **复合格式扩展名注意事项（如 `.tar.gz` / `.tar.xz` / `.tar.bz2`）**
>
> - `getSupportedCompressExtensions()` 和 `getSupportedDecompressExtensions()` 必须返回**完整的复合扩展名**字符串，
>   例如 `".tar.gz"` 而不是 `".gz"` 或 `".tar"`。`ArchiveManager` 在匹配格式时会按最长扩展名优先匹配，
>   因此完整复合扩展名能保证被正确路由到对应引擎，而不会被单后缀引擎（如纯 gzip 引擎）错误接管。
> - 若一个 Provider 同时处理 `.tar` 和 `.tar.gz` 等多个复合变体，需将**所有变体**逐一列入返回集合中。
> - `createDecompressor` 接收到资源后，应根据实际传入的扩展名（可通过 `ArchiveEngineProperty` 或文件名判断）
>   选择对应的解压/解压缩流策略；不可硬编码为单一格式。

## 3. 执行器实现约定

### 压缩器 `ArchiveEngineCompressor`

- `addArchiveResource(ArchiveResource resource)` 必须执行实际写入，且阻塞到当前资源写入完成。
- 若 `resource.isDirectory=true`，应按目录条目写入。
- `close()` 负责完成归档并释放资源。

### 解压器 `ArchiveEngineDecompressor`

- `getArchiveResources()` 返回压缩包中的资源元信息迭代器。
- `getInputStream(String archivePath)` 按完整路径返回资源流。
- `close()` 完成解压，释放占用的资源。

> **`getArchiveResources()` 实现注意事项**
>
> - **禁止预加载**：不得在方法内部将压缩包所有条目全量转换为集合后再返回其迭代器。  
>   应当封装压缩库的原始迭代器（如 `FileHeader` 列表迭代器、`TarArchiveEntry` 枚举等），
>   在迭代器的 `next()` 中按需将原始条目转换为 `ArchiveResource`，以降低内存开销，
>   并让调用方可以在任意位置提前终止遍历。
> - **顺序读取格式**（如 7z `SevenZFile#getNextEntry()`）：  
>   应使用内部状态迭代器顺序读取，迭代结束或解压器 `close()` 时须确保底层资源已释放，
>   避免资源悬挂。

> **`getInputStream(String archivePath)` 实现注意事项**
>
> - 路径匹配前须对传入路径和条目路径进行统一规范化（去除前导 `/`、统一分隔符），
>   避免因路径格式不一致导致查找失败。
> - 若指定路径不存在或对应的是目录条目，应抛出异常而非返回 `null`。
> - 返回的 `InputStream` 由调用方负责关闭，实现类不得在返回后自行关闭。
> - 当需要解压全部文件时，应使用`decompressAll`方法。
 

> **`decompressAll(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func)` 实现注意事项**
> 
> `decompressAll`方法的实现中如果能通过顺序迭代或按Entry获取流实现时，应覆写该方法按照顺序迭代获取流，可参考`RarArchiveEngineDecompressor`。

> **其他注意事项**：不要忘记在 `close()` 中释放临时文件、关闭相关的流。

## 4. 注册引擎

### Spring 自动注册（推荐）

`ArchiveAutoConfiguration` 会从 Spring 容器收集所有 `ArchiveEngineProvider` Bean 并自动注册到 `ArchiveManager`：

- `commons-zip`（Apache Commons ZIP）
- `zip4j`（支持 ZIP 加密压缩与解压）
- `commons-7z`（7z 解压）
- `junrar`（支持 rar 解压）

新增 Provider 时，只需声明为 Bean（`@Bean` 或 `@Component`）即可被自动接入。

### 手动注册

通过Spring Bean 注入 `ArchiveManager`，调用 `registerProvider(ArchiveEngineProvider provider)` 方法注册引擎

