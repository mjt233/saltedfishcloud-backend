# 大规模渐进式文件系统重构方案

## 重构目的

1. 增强接口类与方法的语义准确性，强化设计模式
2. 优化挂载外部存储的实现，外部存储并不需要实现完整的`DiskFileSystem`接口，对存储的操作实际并不需要依赖`uid`参数，单独通过`Storage`接口实现存储操作
3. 简化代码，合并 `DiskFileSystemDispatcher` 与 `DefaultFileSystem`，将`DiskFileSystemDispatcher`的存储路由功能转移到`DefaultFileSystem`，使用`DefaultFileSystem`作为主文件系统

## 实施步骤

- [X] 接口语义调整
    - [X] 将`StoreReader`和`StoreWriter`直接并入`DirectRawStoreHandler`
    - [X] `DirectRawStoreHandler`重命名为`Storage`，继承`AutoCloseable`，作为底层存储能力接口
    - [X] 原`DirectRawStoreHandler`实现类重命名为`XxxStorage`，如：`LocalStorage`、`WebDavStorage`
- [X] 外部存储挂载接口语义优化
  - 接口定义重构
    - [X] 定义`StorageFactory`类，负责根据配置创建`Storage`实例、声明和管理`Storage`的元数据，完全接管`DiskFileSystemFactory`的职责，对`DiskFileSystemFactory`进行以下调整：  
      - [X] 接口类`DiskFileSystemFactory`重命名为`StorageFactory`  
      - [X] 类`DiskFileSystemDescribe`重命名为`StorageMetadata`  
      - [X] 方法`getDescribe`重命名为`getMatedata`  
    - [X] 定义`StorageRegistry`接口，负责注册和管理不同类型的`StorageFactory`，抽离的`DiskFileSystemManager`这部分的职责  
      - [X] 转移`registerFileSystem`方法，并重命名为`registerStorageFactory`  
      - [X] 转移`listAllFileSystem`方法，并重命名为`listStorageFactory`  
      - [X] 转移`listPublicFileSystem`方法，并重命名为`listPublicStorageFactory`  
      - [X] 转移`getFileSystem`方法，并重命名为`getStorage`  
      - [X] 转移`getFileSystemFactory`方法，并重命名为`getStorageFactory`  
      - [X] 转移`isSupportedProtocol`  

- [X] 外部存储挂载实现重构
  - 外部存储实现取消对`DiskFileSystem`接口的实现，取消对`RawDiskFileSystem`的依赖。
  - `StorageFactorygetFileSystem`重命名为`getStorage`，返回值改为`Storage`
  - `StorageFactorytestFileSystem`重命名为`testStorage`，参数改为`Storage`
  - `StorageRegistry#getFileSystem`方法返回值改为`Storage`

- [X] 存储实现与存储路由重构
   - [X] 在`DefaultFileSystem`中实现存储路由功能，根据路径前缀或其他标识来路由到不同的`Storage`实例
   - [X] 将`DefaultFileSystem`设置为主文件系统，吞并`DiskFileSystemDispatcher`的存储路由功能


## 后续计划

- [ ] 将搜索功能迁移出`DiskFileSystem`接口
- [ ] 收敛`FileRecordService`接口，作为文件系统的内部实现，其他模块去掉基于文件所在节点id的硬依赖
- [ ] 当存储模式为`RAW`时，文件系统支持停用`FileRecordService`，完全基于存储进行文件操作

   