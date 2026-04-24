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

## 3. 执行器实现约定

### 压缩器 `ArchiveEngineCompressor`

- `addArchiveResource(ArchiveResource resource)` 必须执行实际写入，且阻塞到当前资源写入完成。
- 若 `resource.isDirectory=true`，应按目录条目写入。
- `close()` 负责完成归档并释放资源。

### 解压器 `ArchiveEngineDecompressor`

- `getArchiveResources()` 返回压缩包中的资源元信息。
- `getInputStream(String archivePath)` 按完整路径返回资源流。
- `close()` 完成解压，释放占用的资源

> 注意：不要忘记在 `close()` 中释放临时文件、关闭相关的流。

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

