# 大规模渐进式文件系统重构方案

## 重构目的

1. 增强接口类与方法的语义准确性，强化设计模式
2. 优化挂载外部存储的实现，外部存储并不需要实现完整的`DiskFileSystem`接口，对存储的操作实际并不需要依赖`uid`参数，单独通过`Storage`接口实现存储操作
3. 简化代码，合并 `DiskFileSystemDispatcher` 与 `DefaultDiskFileSystem`，将`DiskFileSystemDispatcher`的存储路由功能转移到`DefaultDiskFileSystem`，使用`DefaultDiskFileSystem`作为主文件系统

## 实施步骤

1. [ ] 接口语义调整
  - [ ] 将`StoreReader`和`StoreWriter`直接并入`DirectRawStoreHandler`
  - [ ] `DirectRawStoreHandler`重命名为`Storage`，继承`AutoCloseable`，作为底层存储能力接口
  - [ ] 原`DirectRawStoreHandler`实现类重命名为`XxxStorage`，如：`LocalStorage`、`WebDavStorage`
2. [ ] 外部存储挂载优化
  - 接口定义重构
    - [ ] 定义`StorageFactory`类，负责根据配置创建`Storage`实例、声明和管理`Storage`的元数据，完全接管`DiskFileSystemFactory`的职责，对`DiskFileSystemFactory`进行以下调整：
      - 接口类`DiskFileSystemFactory`重命名为`StorageFactory`
      - 类`DiskFileSystemDescribe`重命名为`StorageMetadata`
      - 方法`getDescribe`重命名为`getMatedata`
      - `getFileSystem`重命名为`getStorage`，返回值改为`Storage`
      - `testFileSystem`重命名为`testStorage`，参数改为`Storage`
    - [ ] 定义`StorageRegistry`接口，负责注册和管理不同类型的`StorageFactory`，抽离的`DiskFileSystemManager`这部分的职责
      - 转移`registerFileSystem`方法，并重命名为`registerStorageFactory`
      - 转移`listAllFileSystem`方法，并重命名为`listStorageFactory`
      - 转移`listPublicFileSystem`方法，并重命名为`listPublicStorageFactory`
      - 转移`getFileSystem`方法，并重命名为`getStorage`，返回值改为`Storage`
      - 转移`getFileSystemFactory`方法，并重命名为`getStorageFactory`
      - 转移`isSupportedProtocol`
    - [ ] 实现`StorageFactory`的默认实现，支持本地文件系统和常见云存储（如S3、MinIO、Local）
    - [ ] `DiskFileSystemDispatcher`通过`StorageFactory`获取`Storage`实例，消除对具体实现的依赖
  - 存储实现与存储路由重构
    - [ ] 将原`DiskFileSystemDispatcher`中的存储相关逻辑迁移到新的`StorageFactory`实现中
    - [ ] `DiskFileSystemDispatcher`专注于文件系统调度和管理，调用`StorageFactory`获取底层存储能力