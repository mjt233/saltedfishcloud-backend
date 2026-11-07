# 创建插件

本文档介绍如何创建基础的一个咸鱼云插件。

## 0. 前提条件与注意事项

1. 你需要拉取完整的咸鱼云网盘后端代码项目

```shell
git clone git@github.com:mjt233/saltedfishcloud-backend.git
```

2. 即使你的插件实现只有纯前端代码，也需要必须要和`plugin-info.json`一起打包成jar才能作为系统可识别的插件

## 1. 创建 Maven 模块

在 `sfc-ext` 目录下创建一个新的 Maven 模块，父模块指定为 `com.xiaotao:sfc-ext`。

### 1.1 pom.xml 配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.xiaotao</groupId>
        <artifactId>sfc-ext</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>sfc-ext-你的插件名称</artifactId>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- 插件实现的依赖 -->
        <!--
            宿主已提供的库不要打包进插件（必须用 <exclusions> 排除其传递依赖）：
            Spring / Spring Boot、Jackson、OkHttp、BouncyCastle（bcprov-jdk18on）等。
            详见文末「插件依赖约定」。
        -->
    </dependencies>

    <build>
        <resources>
            <!-- 静态资源目录 -->
            <resource>
                <directory>src/main/assert</directory>
            </resource>
            <!-- 配置资源目录，filtering 设置为 true -->
            <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
            </resource>
        </resources>
    </build>
</project>
```

**目录说明：**

- `src/main/assert` - 用于放置静态资源文件
- `src/main/resources` - 用于放置配置文件

## 2. 声明插件信息

在 `src/main/resources` 下创建 `plugin-info.json`：

```json
{
  "name": "插件名称",
  "loadType": "merge",
  "author": "作者名称",
  "email": "author@example.com",
  "describe": "插件描述",
  "alias": "插件别名",
  "apiVersion": "^api.version^",
  "version": "^project.version^",
  "autoLoad": [
     "index.umd.js",
     "style.css",
     "yourScript.js",
     "other/dir/file.js"
  ],
  "delayLoadLib": [
     "httpclient-4.5.13.jar"
  ]
}
```

**字段说明：**

| 字段           | 必填 | 说明                                                                                                                                                                                |
|--------------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name         | 是  | 插件唯一标识，应与 artifactId 保持一致                                                                                                                                                         |
| loadType     | 是  | 加载方式，`merge` 表示合并到主上下文类加载器                                                                                                                                                        |
| author       | 是  | 作者名称                                                                                                                                                                              |
| email        | 否  | 作者邮箱                                                                                                                                                                              |
| describe     | 否  | 插件描述                                                                                                                                                                              |
| alias        | 否  | 插件显示名称                                                                                                                                                                            |
| apiVersion   | 是  | 兼容的 API 版本，使用 `^api.version^` 占位符                                                                                                                                                 |
| version      | 是  | 插件版本，使用 `^project.version^` 占位符                                                                                                                                                   |
| autoLoad     | 否  | 需要自动加载的前端资源，目前只支持js和css文件。当配置为`[ "index.umd.js", "style.css" ]`时，在打开前端页面时就会自动加载 src/main/assert/static 下的 `index.umd.js`和`style.css` <br> 插件需要新增前端行为和Vue组件时，通常采用该方式作为插件前端资源的加载入口。 |
| delayLoadLib | 否  | json字符串数组，定义需要延迟加载的第三方库，将在所有插件的第三方库加载完成后，最后加载。案例：\["httpclient-4.5.13.jar"\]                                                                                                      |

## 3. 插件配置参数

如果需要声明插件的可配置参数，请参阅 [插件配置参数](./config-properties.md)。

## 4. Spring Boot 集成

如果插件包含后端功能逻辑，需要进行 Spring Boot 集成配置，请参阅 [Spring Boot 集成](./springboot-integration.md)。

## 5. 开发与调试

开发期间，不必将插件构建打包为 jar 来运行，通过以下步骤可以让系统直接加载插件

1. 以 `develop` profile 启动后端：命令行参数 `--spring.profiles.active=develop`，或环境变量 `SPRING_PROFILES_ACTIVE=develop`。该 profile 使用内置 SQLite 与进程内缓存/锁，无需启动 MySQL（MQ 默认仍使用 Redis）
2. 在仓库根目录的 `config-local.yml`（该文件已在 `.gitignore` 中、不会提交；仅 develop 下由 `application-develop.yml` 自动导入，因此启动工作目录必须是仓库根）中，为 `plugin.extra-resource` 添加一项插件工程目录：`sfc-ext/你的插件项目目录`

    ```yaml
    plugin:
      extra-resource:
        - sfc-ext/你的插件项目目录
    ```

3. 如果插件有配置第三方依赖，请务必对插件的maven模块执行`mvn compile`确保依赖库能得到加载，并提示用户在IDE中刷新依赖信息以便后续能使用`build_project`进行验证。

## 6. 构建插件

在 `sfc-ext` 目录下执行构建：

```bash
mvn clean package
```

构建完成后，插件 jar 包会生成在 `release/ext-available/` 目录下。

将生成的 jar 包复制到 `程序运行路径/ext/` 目录下即可加载插件。

## 7. 插件包结构

最终打包的插件 jar 包应包含以下结构：

```
├─plugin-info.json
├─config-properties.json  （可选）
├─plugin-lib
│    ├─xxxx-1.0.0.jar
│    └─xxxx-1.0.0.jar
└─static
    ├─ index.umd.js
    └─ xxx.jpg
```

**目录说明：**

- `plugin-info.json` - 插件基本信息
- `config-properties.json` - 插件配置项（可选）
- `plugin-lib` - 插件依赖的第三方 jar 包
- `static` - 需要暴露给外部访问的静态资源

## 8. 插件依赖约定

插件采用 merge 方式加载：`plugin-lib/` 中的第三方 jar 会在启动时被加入**以宿主应用为父加载器**的合并类加载器。因此：

1. **宿主已提供的库不得打包进插件**：Spring / Spring Boot、Jackson、OkHttp、BouncyCastle 等。
   用 `mvn dependency:tree -pl sfc-ext/你的插件` 排查传递依赖，并通过 `<exclusions>` 排除。
2. **`plugin-lib` 中不得出现与宿主类路径重名的类**。一旦重名，插件系统会打印
   `外部插件无法加载: xxx.jar，存在以下冲突class：[...]` 并中断启动（`PluginInfoException`）。
   例如插件通过 `sshj` 传递打包了 `bcprov-jdk15on`，而宿主已有 `bcprov-jdk18on`，会因 `org.bouncycastle.**` 重名而加载失败。
3. **多个插件共享同一第三方库时必须保持同名同版本**。框架对「同名同版本」的重复依赖只告警并跳过，
   对「同名不同版本」按版本冲突跳过，但对「构件名不同、类却重叠的同一库」（如 `bcprov-jdk15on` 与 `bcprov-jdk18on`）会直接判定为类冲突。
   因此跨插件共享的库请统一构件名与版本。

> 注：宿主当前由 `spring-cloud-starter` 传递提供 BouncyCastle（`bcprov-jdk18on`），`sfc-core` 不单独声明其版本，
> 因此不要排除该传递依赖；升级 Spring Cloud 后需回归 SFTP / Samba 存储功能。