# 用户网盘文件读写操作

本文档介绍如何在插件或二次开发中对用户网盘文件进行读写操作。

## 1. 获取用户文件系统视图

通过 Spring 注入 `DiskFileSystemManager` bean，获取主文件系统：

```java
@Autowired
private DiskFileSystemManager diskFileSystemManager;

public void doSomething() {
    DiskFileSystem fs = diskFileSystemManager.getMainFileSystem();
    // ... 使用文件系统
}
```

## 2. 特殊用户 ID 参数 uid

在所有文件操作方法中，`uid` 参数用于指定目标用户：

| uid 值 | 含义 |
|--------|------|
| `0` | 公共网盘 |
| `> 0` | 对应用户 ID 的个人网盘 |

## 3. path 参数的两种语义

**重要：** 根据不同方法，`path` 参数有两种语义，使用前务必查看方法对应的 Javadoc：

### 3.1 表示文件所在目录（需配合 name 使用）

部分方法的 `path` 参数仅表示文件所在的目录路径，文件名通过独立的 `name` 参数指定：

```java
// 示例：获取文件资源
Resource resource = fs.getResource(uid, "/docs", "readme.md");

// 示例：获取缩略图
Resource thumbnail = fs.getThumbnail(uid, "/images", "photo.jpg");

// 示例：删除文件
fs.deleteFile(uid, "/docs", List.of("readme.md"));

// 示例：重命名
fs.rename(uid, "/docs", "old.txt", "new.txt");

// 示例：保存文件（savePath 为目标目录）
// (推荐使用该方法，以获得最佳文件哈希计算和文件写入性能)
fs.saveFileByStream(fileInfo, "/docs", os -> DiskFileSystemUtils.saveFile(fileInfo, os));

// 示例：保存文件1（savePath 为目标目录）
// 注意：fileInfo.streamSource需要有值
fs.saveFile(fileInfo, "/docs");

```

### 3.2 表示文件完整路径（包含文件名）

部分方法的 `path` 参数本身就是文件的完整路径，无需额外的 name 参数：

```java
// 示例：判断文件是否存在
boolean exists = fs.exist(uid, "/docs/readme.md");

// 示例：创建目录（含父级目录）
fs.mkdirs(uid, "/docs/archive/2024");

// 示例：移动文件
fs.move(uid, "/docs", "/archive", "readme.md", false);

```

## 4. 常用操作示例

```java
@Autowired
private DiskFileSystemManager diskFileSystemManager;

public void fileOperations(long uid) {
    DiskFileSystem fs = diskFileSystemManager.getMainFileSystem();

    // 判断文件是否存在
    boolean exists = fs.exist(uid, "/docs/readme.md");

    // 读取文件列表
    List<FileInfo>[] files = fs.getUserFileList(uid, "/docs");

    // 读取文件资源
    Resource resource = fs.getResource(uid, "/docs", "readme.md");

    // 删除文件
    fs.deleteFile(uid, "/docs", List.of("readme.md"));
}
```

## 5. 公共网盘操作

```java
// 操作公共网盘（uid = 0）
DiskFileSystem fs = diskFileSystemManager.getMainFileSystem();
boolean exists = fs.exist(0, "/public/announcement.txt");
```
