# 插件配置参数

如果插件需要可配置参数，在 `src/main/resources` 下创建 `config-properties.json`。配置后可在系统管理员后台自动展示。

> 注意：下面的为json配置，项目中的json是不能有注释的，复制下面的案例后记得删除注释

下面示例展示该文件的层级结构，并约定各层级下的名词与作用。

大部分情况下，推荐直接在菜单层级使用`typeRef`引用配置实体类的配置结构免去冗杂的手动编写json，也方便在程序中取值。

```js
[
    // 菜单1
    {
        "name": "插件配置菜单标识1",
        "title": "菜单1",
        // 该菜单下存在哪些配置组，配置组下存在哪些具体的独立配置项 则通过实体类的注解结构化定义
        // 常用于大量的复杂配置，且代码中需要频繁多处引用
        "typeRef": "com.sfc.ext.webdav.model.property.WebDavProperty"
    },
    // 菜单2
    {
        "name": "插件配置菜单标识2",
        "title": "菜单2",
        // 手动配置该菜单下存在哪些配置组和配置项，不需要创建新的实体类
        "nodes": [
            {
                // 配置组
                "name": "配置组2标识",
                "title": "菜单2-配置组1标题",
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
                    },
                    {
                        "name": "item1",
                        "title": "配置组下的nodes元素，为配置项1",
                        "describe": "在且仅在该层级下的配置项，在配置系统中为一条独立的配置值"
                    },
                    {
                        "name": "item2",
                        "title": "配置组下的nodes元素，为配置项2",
                        "describe": "在且仅在该层级下的配置项，在配置系统中为一条独立的配置值，即使继续存在下级嵌套配置组，整体作为json字符串",
                        "inputType": "form",
                        "nodes": [
                            {
                                "name": "item2-grou1",
                                "title": "嵌套表单配置组1",
                                "nodes": [
                                    {
                                        "name": "item2-group1-item1",
                                        "title": "嵌套表单配置项1"
                                    }
                                ]
                            }
                        ]
                    },
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


## 字段说明

| 字段           | 必填 | 说明                                              |
|--------------|----|-------------------------------------------------|
| name         | 是  | 标识前缀                                            |
| title        | 是  | 分组标题                                            |
| nodes        | 否  | 嵌套的配置组节点。当配置了typeRef时可不配置该项，template不为form时的配置项 | 
| describe     | 否  | 配置项选填。配置项的描述                                    |
| defaultValue | 否  | 配置项选填。默认值                                       |
| typeRef      | 否  | 菜单或form配置项可选。配置类的完全限定名，用于绑定配置对象                 |
| inputType    | 否  | 配置项必填。输入类型，见下方[inputType 说明](#inputtype)        |

### inputType 说明

| inputType  | 值类型     | 说明                                               |
|------------|---------|--------------------------------------------------|
| 'switch'   | boolean | 开关组件，值类型为boolean                                 |
| 'select'   | string  | 下拉单选框，需要搭配`options`属性配置可选项                       |
| 'radio'    | string  | 单选框，需要搭配`options`属性配置可选项                         |
| 'text'     | string  | 任意文本输入                                           |
| 'textarea' | string  | 多行文本输入                                           |
| 'form'     | string  | 嵌套子表单，值类型为json字符串，需要搭配`nodes`属性继续嵌套定义子配置组以及组下配置项 |
| 'template' | string  | 使用自定义vue组件或html标签，需要搭配`template`参数指定vue组件或html标签 |

## 示例：简单配置项

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

## 配置参数实体类

前面的【声明插件可配置参数】案例中，存在`"typeRef": "com.sfc.ext.webdav.model.property.WebDavProperty"`
配置项，通过下面的代码实现将配置项的值与Bean绑定

### 声明配置参数实体类

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

### 配置值与Bean绑定

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

### 配置参数的读取

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
