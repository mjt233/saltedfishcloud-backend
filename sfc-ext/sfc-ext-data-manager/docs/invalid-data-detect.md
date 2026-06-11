# 失效数据检测策略

## 1. 物理存储与文件记录扫描

- 通过`storeServiceFactory.getStoreService().getStorageProvider()`获取存储服务底层的存储提供者`Storage`，基于`Storage`的操作对物理存储数据进行检查。
- 通过`fileSystemMetadataOperator`对所有用户的文件记录进行扫描，检查文件记录的有效性。

数据比对：
- 当通过`Storage`物理存储中的数据未存在能被`fileSystemMetadataOperator`有效关联时，说明该文件为失效数据；
- 当通过`fileSystemMetadataOperator`扫描到的文件记录在物理存储中未能被`Storage`扫描到时，说明该文件记录为失效数据。

### Storage的文件列表

需要根据不同的存储模式，设计不同的物理存储路径检查策略

存储模式可通过`sysCommonConfig.getStoreMode()`获取

#### RAW 模式

- 公共网盘（uid=0）： 在`storeServiceFactory.getStoreService().getPublicRoot()`下获取文件列表
- 用户网盘（uid>0）：在{`storeServiceFactory.getStoreService().getStoreRoot`}/user_file/用户id 下获取文件列表

在 publicRoot 或 storeRoot/user_file/用户id 下的文件的物理存储路径和文件名即为该文件的网盘路径和文件名，属于可直接识别的文件。

#### UNIQUE 模式

UNIQUE模式不区分公共网盘和用户网盘，所有文件都存储在`storeServiceFactory.getStoreService().getStoreRoot()`的多层子目录下，所有无拓展名的文件即为物理存储文件。

这些无拓展名的文件名本身即为该文件的md5值。

由于文件仅通过md5值进行存储，无法直接从物理存储路径中识别出文件的网盘路径和文件名，因此属于待识别文件。

## 2. 记录失效数据

失效数据明细需要包含以下字段：

- 失效数据类型（失效文件记录/失效物理存储）
- 完整物理存储路径（对于失效物理存储，表示现存文件的物理存储路径；对于失效文件记录，表示记录指向的物理存储路径）
- 所属用户id（公共网盘文件为0，用户网盘文件>0）
- 网盘路径（对于失效的UNIQUE模式的物理存储，由于无法识别的网盘路径，值为null）
- 文件大小
- 最后修改时间
- 是否为待识别的文件（UNIQUE模式下为无法直接识别的文件，RAW模式无需识别，直接根据拓展名识别即可）
- 文件类型（基于系统支持的文件类型识别的typeId，表示图片、视频、文档、文本等数据，该字段为null表示未知/待识别）
- 元数据（仅UNIQUE模式下对该文件进行识别操作后产生）