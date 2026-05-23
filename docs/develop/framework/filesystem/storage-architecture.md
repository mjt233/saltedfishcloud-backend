# 存储架构与挂载扩展

## 概述

咸鱼云网盘的存储系统采用分层架构设计，核心由两个关键接口构成：`Storage`（原始存储层）和 `DiskFileSystem`
（最终用户网盘视图层）。系统通过这种分层实现了存储后端的可插拔扩展。

在顶层，`DiskFileSystemManager` 管理一个唯一的主文件系统`DiskFileSystem`实例，绝大部分情况下开发者面向该实例编程对文件系统进行操作即可满足需求。

但如果需要对可挂载的文件系统进行扩展，则需要扩展底层的 `Storage` 接口实现，并将其 `StorageFactory` 注册到系统中。

### 关键接口设计介绍

#### DiskFileSystem

`DiskFileSystem` 是系统的**最顶层网盘资源操作接口**，位于 `sfc-api` 模块：

所有对用户网盘资源的调用与操作都应通过该接口进行。它基于 **UID + 网盘路径** 的语义工作，并根据挂载路由解析路径，将存储操作派发到不同的
`Storage`中，提供了面向用户的核心操作：

- `getUserFileList` — 获取目录文件列表
- `getResource` / `getThumbnail` — 读取文件 / 缩略图
- `saveFile` / `saveFileByStream` — 保存文件
- `deleteFile` — 删除文件
- `copy` / `move` / `rename` — 文件操作
- `mkdir` / `mkdirs` — 创建目录

#### Storage

`Storage` 是**原始存储层接口**，位于 `sfc-api` 模块。它直接使用存储服务的原始路径（而非网盘路径）进行资源操作：

```java
public interface Storage extends AutoCloseable {
    boolean delete(String path);

    boolean mkdir(String path);

    long store(FileInfo fileInfo, String path, long size, InputStream inputStream);

    OutputStream newOutputStream(String path);

    boolean rename(String path, String newName);

    boolean copy(String src, String dest, FileTransferItem transferItem);

    boolean move(String src, String dest, FileTransferItem transferItem);

    void updateTime(String path, List<String> names, FileTimeAttribute attribute);

    boolean isEmptyDirectory(String path);

    Resource getResource(String path);

    List<FileInfo> listFiles(String path);

    FileInfo getFileInfo(String path);

    boolean exist(String path);

    boolean mkdirs(String path);
}
```

### 现有实现

系统已内置多种 `Storage` 实现：

| 实现类             | 模块                   | 说明                            |
|-----------------|----------------------|-------------------------------|
| `LocalStorage`  | sfc-core             | 本地文件系统存储                      |
| `ScopedStorage` | sfc-api              | 路径作用域包装器，为其他 Storage 添加基础路径隔离 |
| `WebDavStorage` | sfc-ext-webdav-store | WebDAV 协议远程存储                 |
| `FTPStorage`    | sfc-ext-ftp-store    | FTP 协议存储                      |
| `SFTPStorage`   | sfc-ext-sftp-store   | SFTP 协议存储                     |
| `SambaStorage`  | sfc-ext-samba-store  | Samba/CIFS 协议存储               |
| `MinioStorage`  | sfc-ext-minio-store  | MinIO 对象存储                    |
| `S3Storage`     | sfc-ext-oss-store    | 兼容 S3 协议的对象存储                 |
| `HDFSStorage`   | sfc-ext-hadoop-store | HDFS 分布式存储                    |

## 存储扩展机制

系统通过 `StorageFactory` 工厂接口统一管理各种存储后端的创建与生命周期。

### 架构链

```
StorageFactory (工厂接口，创建并缓存同一类的 Storage 实例，并维护该类存储的元数据)
    |
    v
Storage (原始存储操作，基于原始路径)
    |
    v
ScopedStorage (装饰器，添加基础路径作用域隔离，非必要但建议所有可配置路径的挂载存储使用)
    |
    v
StorageDiskFileSystemAdapter (适配器，将 Storage 适配为 DiskFileSystem，用作内部主文件系统与挂载存储的路由过渡组件)
    |
    v
DiskFileSystem (最终用户网盘视图，基于 UID + 网盘路径)
```

### StorageFactory

每个存储类型都需要实现 `StorageFactory` 接口，负责根据参数创建对应的 `Storage` 实例并管理缓存：

```java
public interface StorageFactory {
    Storage getStorage(Map<String, Object> params);

    void testStorage(Storage storage);

    void clearCache(Collection<Map<String, Object>> params);

    void clearCache(Map<String, Object> params);

    StorageMetadata getMetadata();
}
```

通常推荐继承 `AbstractStorageFactory` 来简化实现，它已内置了缓存机制和 `clearCache` 实现。

### StorageMetadata

每个工厂通过 `getMetadata()` 返回 `StorageMetadata` 描述信息，用于管理系统识别和前端展示：

```java

@Getter
@Builder
public class StorageMetadata {
    private final String name;           // 显示名称
    private final String protocol;       // 协议标识
    private final String describe;       // 描述信息
    private final boolean isPublic;      // 是否允许任意用户创建挂载点
    private final Collection<ConfigNode> configNode;  // 参数配置节点
}
```

## 开发实战 - 存储扩展

下面以 `WebDavStorage` 为例，说明如何为系统新增一个外部挂载存储扩展。

### 完整步骤

#### 1. 创建属性配置类

定义用户在后台创建挂载点时需要填写的参数：

```java

@Data
@ConfigPropertyEntity(
        groups = {
                @ConfigPropertiesGroup(id = "base", name = "服务器信息"),
                @ConfigPropertiesGroup(id = "auth", name = "认证信息"),
        },
        defaultKeyNameStrategy = ConfigKeyNameStrategy.CAMEL_CASE
)
public class MyStorageProperty {
    @ConfigProperty(title = "主机地址", required = true)
    private String host;

    @ConfigProperty(title = "目标目录")
    private String basePath;

    @ConfigProperty(group = "auth", title = "用户名")
    private String username;

    @ConfigProperty(group = "auth", title = "密码", isMask = true)
    private String password;
}
```

#### 2. 实现 Storage 接口

实现最底层的文件操作逻辑：

```java

@Slf4j
public class MyStorage implements Storage {

    private final MyStorageProperty property;

    public MyStorage(MyStorageProperty property) {
        this.property = property;
    }

    // 实现 Storage 接口的各个方法，调用对应存储服务的 SDK 或 API 进行操作
    // 注意：
    //   如果Property中支持用户自定义目标存储的路径，在 Storage 中不必对路径进行拼接
    //   只需要根据 SDK 或 API的需求进行必要的规范化或转换即可
    //       （如斜杠/转为反斜杠\，需要进行URL编码等）
    //   路径的拼接可利用ScopedStorage进行统一处理，确保路径安全和规范
    // 系统调用Storage时路径统一采用斜杠分隔符/
}
```

#### 3. 实现 StorageFactory

继承 `AbstractStorageFactory`，实现参数解析和存储实例创建：

```java
public class MyStorageFactory extends AbstractStorageFactory<MyStorageProperty, Storage> {

    @Override
    public MyStorageProperty parseProperty(Map<String, Object> params) {
        return ObjectUtils.mapToBean(params, MyStorageProperty.class);
    }

    @Override
    public Storage generateStorage(MyStorageProperty property) throws IOException {
        // 如有必要，建议使用 ScopedStorage 包装，添加基础路径作用域隔离防止路径越界操作
        return new ScopedStorage(
                new MyStorage(property),
                Optional.ofNullable(property.getBasePath())
                        .filter(StringUtils::hasText)
                        .orElse("/")
        );
    }

    @Override
    public StorageMetadata getMetadata() {
        return StorageMetadata.builder()
                
                // 该存储的介绍
                .describe("我的自定义远程存储服务")
                
                // 该存储展示的名称
                .name("MyStorage")
                
                // 是否公开允许所有用户创建挂载点，false则只能由管理员创建挂载点
                .isPublic(true)
                
                // 存储唯一标识
                .protocol("mystorage")
                
                // 创建挂载点时需要填写的参数配置项
                .configNode(PropertyUtils.getConfigNodeFromEntityClass(MyStorageProperty.class).values())
                .build();
    }
}
```

#### 4. 注册为 Spring Bean

在 `@Configuration` 类中将工厂声明为 Bean：

```java

@Configuration
public class MyStorageAutoConfiguration {
    @Bean
    public MyStorageFactory myStorageFactory() {
        return new MyStorageFactory();
    }
}
```

#### 5. 配置插件描述

在插件模块的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件中注册自动配置类：

```
com.example.mystorage.MyStorageAutoConfiguration
```

### 完成效果

完成以上步骤后，系统管理员即可在后台「挂载存储」页面看到新的存储类型，创建挂载点后该存储会自动生效，用户可在网盘中通过挂载路径访问。

## 关键类参考

| 类                        | 所在模块    | 说明         |
|--------------------------|---------|------------|
| `Storage`                | sfc-api | 原始存储操作接口   |
| `DiskFileSystem`         | sfc-api | 用户网盘视图接口   |
| `StorageFactory`         | sfc-api | 存储工厂接口     |
| `AbstractStorageFactory` | sfc-api | 带缓存的抽象工厂基类 |
| `ScopedStorage`          | sfc-api | 路径作用域装饰器   |
| `StorageMetadata`        | sfc-api | 存储元数据描述    |
