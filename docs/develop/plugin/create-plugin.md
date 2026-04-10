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
        <!-- 若存在重复的传递依赖，请尽量手动排除 -->
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
    "xxx"
  ]
}
```

**字段说明：**

| 字段         | 必填 | 说明                                                                                                                                                                         |
|------------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name       | 是  | 插件唯一标识，应与 artifactId 保持一致                                                                                                                                                  |
| loadType   | 是  | 加载方式，`merge` 表示合并到主上下文类加载器                                                                                                                                                 |
| author     | 否  | 作者名称                                                                                                                                                                       |
| email      | 否  | 作者邮箱                                                                                                                                                                       |
| describe   | 否  | 插件描述                                                                                                                                                                       |
| alias      | 否  | 插件显示名称                                                                                                                                                                     |
| apiVersion | 是  | 兼容的 API 版本，使用 `^api.version^` 占位符                                                                                                                                          |
| version    | 是  | 插件版本，使用 `^project.version^` 占位符                                                                                                                                            |
| autoLoad   | 否  | 需要自动加载的前端资源，目前只支持js和css文件。当配置为`[ "index.umd.js", "style.css" ]`时，在打开前端页面时就会自动加载 src/main/assert 下的 `index.umd.js`和`style.css` <br> 插件需要新增前端行为和Vue组件时，通常采用该方式作为插件前端资源的加载入口。 |

## 3. 声明插件可配置参数（可选）

如果插件需要可配置参数，在 `src/main/resources` 下创建 `config-properties.json`。配置后可在系统管理员后台自动展示。

> 注意：下面的为json配置，项目中的json是不能有注释的，复制下面的案例后记得删除注释

```js
[
    // 配置菜单1
    {
        "name": "插件配置菜单标识1",
        "title": "标题1",
        // 该菜单下存在哪些配置组，配置组下存在哪些具体的独立配置项 则通过实体类的注解结构化定义
        // 常用于大量的复杂配置，且代码中需要频繁多处引用
        "typeRef": "com.sfc.ext.webdav.model.property.WebDavProperty"
    },
    // 配置菜单2
    {
        "name": "插件配置菜单标识2",
        "title": "标题2",
        // 手动配置该菜单下存在哪些配置组和配置项，不需要创建新的实体类
        "nodes": [
            {
                // 配置组
                "name": "配置组2标识",
                "title": "配置组2标题",
                "nodes": [
                    // 配置项1
                    {
                        "name": "sys.safe.allow_anonymous_comments",
                        "title": "匿名留言",
                        "describe": "允许匿名留言",
                        "defaultValue": false,
                        "inputType": "switch"
                    },
                    // 配置项2
                    {
                        "name": "sys.safe.token",
                        "title": "系统安全密钥",
                        "describe": "系统的安全密钥，修改后会导致所有用户登录失效、直链失效",
                        "defaultValue": "",
                        "inputType": "text",
                        "hide": true
                    }
                ]
            }
        ]
    },
    // 配置菜单3
    {
        // 该菜单不作为系统统一的配置项，直接加载一个指定的vue组件作为该菜单的页面
        // 关键配置: inputType设置为"template", template设置为需要加载的vue组件名称
        "name": "插件配置菜单标识3",
        "title": "特殊功能",
        "nodes": [{
            "name": "功能标识",
            "title": "特殊功能名称",
            "inputType": "template",
            "template": "your-custom-vue-component-name"
        }]
    }
]
```

**字段说明：**

| 字段                   | 必填 | 说明                       |
|----------------------|----|--------------------------|
| name                 | 是  | 配置项标识前缀                  |
| title                | 是  | 配置分组标题                   |
| nodes                | 否  | 嵌套的配置节点                  |
| nodes[].name         | 是  | 具体配置项的标识                 |
| nodes[].title        | 是  | 配置项的显示标题                 |
| nodes[].describe     | 否  | 配置项的描述                   |
| nodes[].defaultValue | 是  | 默认值                      |
| nodes[].typeRef      | 否  | 配置类的完全限定名，用于绑定配置对象       |
| nodes[].inputType    | 否  | 输入类型，如 `form`、`template` |

**示例：简单配置项**

```json
[
  {
    "name": "my-plugin",
    "title": "基础配置",
    "defaultValue": "{}",
    "typeRef": "com.example.plugin.MyPluginConfig"
  }
]
```

## 4. SpringBoot 集成（可选）

当你的插件包含后端功能逻辑时，通常需要配置该项

### 4.1 配置spring boot autoconfigure

1. 在插件的`src/main/resources/META-INF/spring`下，创建文件`org.springframework.boot.autoconfigure.AutoConfiguration.imports`
2. 文件内容填写你的插件Spring Bean配置入口类全限定路径名称，如：`com.sfc.staticpublish.config.StaticPublishAutoConfiguration`

### 4.2 注册配置参数实体对象

前面的【声明插件可配置参数】案例中，存在`"typeRef": "com.sfc.ext.webdav.model.property.WebDavProperty"`配置项，通过下面的代码实现将配置项的值与Bean绑定

#### 4.2.1 声明配置参数实体类

关键点：该实体类需要使用`@ConfigPropertyEntity`注解标注

```java

@Data
@ConfigPropertyEntity(prefix = "webdav")
public class WebDavProperty {
    @ConfigProperty(title = "功能开关", describe = "开启 WebDAV 服务", inputType = "switch", defaultValue = "false")
    private Boolean isEnable = false;

    @ConfigProperty(title = "监听地址", describe = "WebDAV 服务器实际监听的地址，可为空")
    private String listenIp = "";

    @ConfigProperty(title = "监听端口", required = true, describe = "WebDAV 服务器实际监听的端口", defaultValue = "8086")
    private Integer listenPort = 8086;

    @ConfigProperty(title = "展示的服务地址", describe = "仅用于用户查看 WebDAV 信息配置页面中显示的地址。当通过其他Web服务反向代理原始的WebDAV服务后，引导用户访问经过反代后暴露的地址")
    private String displayUrl;
}

```

#### 4.2.2 配置值与Bean绑定

```java
package com.sfc.ext.webdav;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.sfc.ext.webdav.model.property.WebDavProperty;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

// 为了加快启动速度，如无必要不建议配置包扫描
@Slf4j
@Configuration
@Import({
        Xxxx其他要自动注册的Bean1.class,
        Xxxx其他要自动注册的Bean2.class,
})
public class WebDavAutoConfiguration implements ApplicationRunner {

    @Bean
    public WebDavProperty webDavProperty(ConfigService configService, HelloService helloService) {
        // 例如：WebDavProperty作为config-properties.json中的 配置菜单的typeRef，需要通过该方式将配置值与对象同步绑定
        
        // 1. 创建对象空实例
        WebDavProperty property = new WebDavProperty();
        
        // 2. 绑定到统一配置服务，对应配置项被修改后，会同步更新到property对象
        configService.bindPropertyEntity(property);
        
        // 3. (可选)绑定系统全局特性，前端可通过全局的 getContext().feature.feature['webDavConfig'] 便捷拿到对应的值或对象
        WebDavPropertyVO vo = new WebDavPropertyVO(property);
        helloService.setFeature("webDavConfig", () -> vo);
        
        return property;
    }
}

```

#### 4.2.3 配置参数的读取

**方式一：通过与配置项绑定的配置参数实体类Bean（推荐）**

```java
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public class YourClass {
    // 通过构造器注入（推荐）
    private final WebDavProperty property;
    
    // 通过 Spring 字段注入
    @Autowired
    private WebDavProperty property;
}

```

**方式二：手动获取指定配置key**
```java

import com.xiaotao.saltedfishcloud.service.config.ConfigService;

@RequiredArgsConstructor
public class YourClass {
    // 通过构造器注入（推荐）
    private final ConfigService configService;
    
    public void yourMethod() {
        // 方式一：解析配置实体类Lambda，自带类型转换（推荐）
        // 要求：存在对应的参数配置实体类
        Integer value = configService.getConfig(WebDavProperty::getListenPort);
        
        // 方式二：配置key硬编码
        String value = configService.getConfig("your-config-key");
    }
}
```

## 4. 开发与调试

开发期间，不必将插件构建打包为jar来运行，通过以下步骤可以让系统直接加载插件

1. maven的profile设置为`develop`，或指定SpringBoot配置文件为`sfc-core/src/main/config/application-develop.yml`（下面简称为`application-develop.yml`）
2. 修改`application-develop.yml`，为`plugin.extra-resource`添加一项: `sfc-ext/你的插件项目目录`
3. 如果插件有配置第三方依赖，请务必对插件的maven模块执行`mvn compile`确保依赖库能得到加载

## 5. 构建插件

在 `sfc-ext` 目录下执行构建：

```bash
mvn clean package
```

构建完成后，插件 jar 包会生成在 `release/ext-available/` 目录下。

将生成的 jar 包复制到 `程序运行路径/ext/` 目录下即可加载插件。

## 6. 插件包结构

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