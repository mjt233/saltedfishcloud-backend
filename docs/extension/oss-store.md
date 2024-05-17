# oss-store

## 简介

该插件可以为咸鱼云提供第三方云服务的对象存储（OSS）服务的目录挂载功能，如：阿里云、腾讯云、Amazon S3。

目前该插件只实现了基于Amazon S3协议访问的OSS服务，国内的厂商如：阿里云、腾讯云均有对其兼容支持。

其他的对象存储服务如：MinIO也有对S3的支持，因此目前实现的该协议支持泛用性较高。

插件minio-store后续将被弃用并将其内容合并到该插件中。


## 注意事项

### 注意OSS接口调用费用
使用对象存储服务挂载存储目录后，对该目录的任何操作包括但不限于：复制、剪切、重命名、列出文件列表、下载、上传文件、创建分享链接、创建文件夹等任何文件操作，咸鱼云都会对OSS服务发起接口调用。

如果挂载的OSS服务商是会根据接口调用类型、次数进行收费的，请务必留意以免造成不必要的费用开销。

咸鱼云网盘系统的挂载功能后续将支持让咸鱼云托管其文件系统的文件记录服务，可以大幅减少其接口调用频率并纳入文件快速搜索范围。

### 注意有效期

并不是所有OSS服务都支持7天的URL有效期，挂载选项中选择7天可能会导致文件下载的URL生成失败，此时可考虑把有效期改为3天。

## 拓展存储协议（面向开发者）

如果您的OSS服务提供商不提供Amazon S3协议的支持，那么你可以尝试自己根据厂商提供的SDK来实现接口访问功能。

该插件提供了相关的拓展接口以便您自行拓展这部分功能，项目将介绍如何拓展支持的OSS协议。

### 初始化项目

在项目`sfc-ext`下创建你的OSS拓展maven项目，以sfc-ext为上级项目，添加oss-store的maven依赖。参考pom.xml如下
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>sfc-ext</artifactId>
        <groupId>com.xiaotao</groupId>
        <version>1.0.0</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <version>1.0.0</version>

    <artifactId>sfc-ext-your-oss-store-extension</artifactId>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.xiaotao</groupId>
            <artifactId>sfc-ext-oss-store</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

在resources下创建文件`plugin-info.json`来声明插件信息，模板如下:
```json
{
  "name": "插件名称",
  "loadType": "merge",
  "author": "作者名称",
  "icon": "插件图标，取mdi-{图标代码}，使用mdi-icon库图标",
  "email": "作者联系邮箱",
  "describe": "插件描述",
  "alias": "插件别名",
  "apiVersion": "^api.version^",
  "version": "^project.version^",
  "delayLoadLib": [
    "需要在系统启动完成后才加载的第三方库文件以防止干扰系统启动流程导致意外，如:joda-time-2.8.1.jar"
  ]
}
```

### 实现OSS的存储接口

可参考`com.sfc.ext.oss.store.S3DirectRawHandler`

### 注册OSS存储接口

从Spring中获取bean `ossDiskFileSystemFactory`，调用`registerOSSStoreType`方法即可注册。

### 存在的缺陷以及临时解决方案

目前固定死了参数类型为`OSSProperty`，不好拓展。因此也可以考虑像minio-store插件一样单独作为一个存储挂载类型，而不是OSS存储下的一个类型。