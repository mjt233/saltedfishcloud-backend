# ConfigService 与 HelloService 使用说明

本文面向开发手册，介绍 `ConfigService` 与 `HelloService` 的职责边界、常见用法与联动模式。

## 职责与关系

`ConfigService` 负责统一配置读取、写入、监听与配置实体绑定。  
`HelloService` 负责统一暴露系统特性（feature）给前端与调用方。  
常见实践是：先把配置绑定到对象，再将配置或对象映射到 feature，实现“配置变更 -> 特性自动更新”。

## ConfigService 常用用法

### 1) 配置实体绑定（推荐）

适用场景：插件或模块存在一组稳定配置，需要在运行中自动同步。

```java
@Bean
public WebDavProperty webDavProperty(ConfigService configService) {
    WebDavProperty property = new WebDavProperty();
    configService.bindPropertyEntity(property);
    return property;
}
```

要点：

- `property` 类需要 `@ConfigPropertyEntity` 注解。
- 字段使用 `@ConfigProperty` 声明配置项。
- 调用 `bindPropertyEntity` 后会立即同步一次当前配置，并在后续变更时自动更新字段值。

参考：`sfc-ext/sfc-ext-webdav/src/main/java/com/sfc/ext/webdav/WebDavAutoConfiguration.java`

### 2) 读取与写入配置

```java
String token = configService.getConfig(SysConfigName.Safe.TOKEN);
Integer port = configService.getConfig(WebDavProperty::getListenPort);
Boolean ok = configService.setConfig(SysConfigName.Safe.TOKEN, "new-secret");
```

要点：

- 推荐优先使用 Lambda 方式（如 `WebDavProperty::getListenPort`），可减少硬编码 key 并自动做类型转换。
- 直接 key 方式适用于临时配置或无实体类映射场景。

### 3) 配置变更监听

```java
configService.addBeforeSetListener(SysConfigName.Store.SYS_STORE_TYPE, val -> {
    // 写入前触发
});

configService.addAfterSetListener(SysConfigName.Safe.TOKEN, JwtUtils::setSecret);
```

要点：

- `addBeforeSetListener`：配置写库前触发，适合前置校验。
- `addAfterSetListener`：配置变更广播后触发，适合刷新内存状态与运行时对象。

参考：`sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/SysCommonConfigConfiguration.java`

> 对于变更后触发数据刷新的场景，推荐优先使用“配置实体 + `bindPropertyEntity`”的方式，避免监听器中手动同步数据。

## HelloService 常用用法

### 1) 注册静态特性

```java
helloService.setFeature("version", sysProperties.getVersion().toString());
```

适用于版本号、固定开关、固定入口路径等值。

参考：`sfc-core/src/main/java/com/xiaotao/saltedfishcloud/init/StartRecord.java`

### 2) 注册动态特性

```java
helloService.setFeature("webDavConfig", () -> vo);
```

动态特性在读取时执行 `Supplier`，适合需要按当前内存状态实时返回的数据。

参考：`sfc-ext/sfc-ext-webdav/src/main/java/com/sfc/ext/webdav/WebDavAutoConfiguration.java`

### 3) 追加集合类特性

```java
helloService.appendFeatureDetail("fileSystem", "local");
```

当同名特性已存在且为 `Collection` 时会继续追加；可用于声明“支持能力列表”。

### 4) 将配置直接绑定为特性

```java
helloService.bindConfigAsFeature(SysConfigName.Theme.DARK, FeatureName.DARK_THEME, Boolean.class);
helloService.bindConfigAsFeature(SysCommonConfig::getIsUseCommonUpload, FeatureName.IS_USE_COMMON_UPLOAD, Boolean.TRUE);
```

要点：

- 绑定时会立即读取当前配置并写入特性。
- 配置后续变更会自动同步到对应 feature。
- 支持 key 字符串和 Lambda 两种方式，Lambda 方式同样更推荐。

参考：`sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/SysCommonConfigConfiguration.java`

## 前端与接口可见性

默认通过匿名接口获取特性清单：

- 接口：`GET /api/hello/feature`
- 控制器：`sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/HelloController.java`

后端通过 `helloService.getAllFeatureDetail()` 返回全部特性详情，动态特性会在返回前完成求值。

## 联动实践建议

- 优先“配置实体 + `bindPropertyEntity`”管理复杂配置。
- 对需要暴露给前端的配置，优先使用 `bindConfigAsFeature`，避免重复监听与手动同步。
- 静态值用 `setFeature(name, object)`，运行态值用 `setFeature(name, supplier)`。
- 面向能力列表的场景使用 `appendFeatureDetail`，避免同 key 多处覆盖。或使用运行动态值的方式，通过`setFeature(name, supplier)`返回一个对象引用。

