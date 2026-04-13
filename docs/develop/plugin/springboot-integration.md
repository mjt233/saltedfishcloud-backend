# Spring Boot 集成

当你的插件包含后端功能逻辑时，需要进行以下配置来实现 Spring Boot 自动集成。

## 配置 Spring Boot Autoconfigure

### 1. 创建自动配置文件

在插件的 `src/main/resources/META-INF/spring` 下，创建文件 `org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 2. 填写配置类路径

文件内容填写你的插件 Spring Bean 配置入口类全限定路径名称，如：

```
com.sfc.staticpublish.config.StaticPublishAutoConfiguration
```
