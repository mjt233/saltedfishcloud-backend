# 桌面组件注册

插件可以通过在插件资源根目录下放置 `desktop-component.json` 文件，向系统注册桌面小组件。系统启动时会自动扫描所有插件中的该文件并注册其中的组件。

## 1. 配置文件位置

在插件的资源目录（`src/main/resources/`）下创建 `desktop-component.json` 文件：

```
sfc-ext-{插件id}/
└── src/
    └── main/
        └── resources/
            └── desktop-component.json
```

## 2. JSON 结构说明

`desktop-component.json` 是一个 JSON 数组，每个元素定义一个桌面组件。

### 2.1 组件定义

| 字段          | 类型           | 必填 | 说明                                                                                                                          |
|-------------|--------------|----|-----------------------------------------------------------------------------------------------------------------------------|
| `name`      | String       | 是  | 组件唯一标识名称，同时也是Vue组件的name，不可重复，命名采用全小写的横杠命名法（KEBAB_CASE）                                                                      |
| `title`     | String       | 是  | 组件标题，在桌面组件选择器中显示                                                                                                            |
| `describe`  | String       | 否  | 组件描述，简要说明组件用途                                                                                                               |
| `icon`      | String       | 否  | 图标类名，支持 Material Design Icons、url、base64，如 `mdi-clock-outline`、`http://sample.com/image.jpg` 、 `data:image/png;base64 xxxx` |
| `showOrder` | Integer      | 否  | 显示顺序，数值越小越靠前，默认 0                                                                                                           |
| `scope`     | String       | 否  | 组件生效范围：`"public"`（仅公共桌面）、`"private"`（仅我的桌面）、不填或 `null` 表示通用                                                                 |
| `config`    | ConfigNode[] | 否  | 组件配置参数原型列表，用户添加组件时可自定义配置参数，仅支持单层                                                                                            |

### 2.2 配置参数（ConfigNode）

`config` 数组中的每个元素定义了一个可配置参数，支持的字段如下：

| 字段             | 类型                 | 必填 | 说明                                                    |
|----------------|--------------------|----|-------------------------------------------------------|
| `name`         | String             | 是  | 参数名称，作为配置标识键                                          |
| `title`        | String             | 否  | 参数标题，在配置界面中显示                                         |
| `describe`     | String             | 否  | 参数描述说明                                                |
| `inputType`    | String             | 否  | 输入类型，详见下方说明                                           |
| `defaultValue` | String             | 否  | 默认值                                                   |
| `required`     | Boolean            | 否  | 是否必填，默认 false                                         |
| `isRow`        | Boolean            | 否  | 是否独占一行，默认 false                                       |
| `template`     | String             | 否  | 当 `inputType` 为 `"template"` 时，使用的模板名称                |
| `params`       | Map<String,Object> | 否  | 额外参数，当类型为 `template` 时会传递给模板                          |
| `options`      | SelectOption[]     | 否  | 可选项，用于 `select`、`multi-select`、`radio`、`checkbox` 等类型 |

### 2.3 输入类型（inputType）

| 值              | 说明                         |
|----------------|----------------------------|
| `text`         | 单行文本输入                     |
| `number`       | 数字输入                       |
| `switch`       | 开关选择器                      |
| `select`       | 下拉选择                       |
| `multi-select` | 多选下拉                       |
| `radio`        | 单选按钮                       |
| `checkbox`     | 多选复选框                      |
| `textarea`     | 多行文本输入                     |
| `template`     | 模板内容编辑，需配合 `template` 字段使用 |

### 2.4 模板类型（template）

当 `inputType` 为 `"template"` 时，通过 `template` 字段指定具体的编辑模板 或 Vue组件名称：

| 模板值                | 说明                                                        |
|--------------------|-----------------------------------------------------------|
| `markdown-input`   | Markdown 编辑器                                              |
| `form-code-editor` | 代码编辑器，可通过 `params.language` 指定语言（如 `html`、`javascript` 等） |

### 2.5 选项定义（SelectOption）

为 `select`、`multi-select`、`radio`、`checkbox` 等类型提供可选值时，每个选项定义如下：

| 字段      | 类型     | 必填 | 说明     |
|---------|--------|----|--------|
| `value` | String | 是  | 选项值    |
| `title` | String | 是  | 选项显示文本 |

## 3. 示例

### 3.1 基础示例

以下示例注册一个名为"网络唤醒"的桌面组件，提供三个开关配置参数：

```json
[
  {
    "name": "wol-device-list",
    "title": "WOL网络唤醒",
    "describe": "管理你的WOL设备",
    "icon": "mdi-power-standby",
    "showOrder": 0,
    "config": [
      {
        "name": "showAdd",
        "inputType": "switch",
        "describe": "能否新增",
        "defaultValue": false
      },
      {
        "name": "editable",
        "inputType": "switch",
        "describe": "能否编辑修改",
        "defaultValue": false
      }
    ]
  }
]
```

### 3.2 完整示例

```json
[
  {
    "name": "my-weather",
    "title": "天气组件",
    "describe": "显示当前天气信息",
    "icon": "mdi-weather-sunny",
    "showOrder": 1,
    "scope": "private",
    "config": [
      {
        "name": "city",
        "title": "城市名称",
        "inputType": "text",
        "describe": "输入要显示天气的城市",
        "required": true,
        "defaultValue": "深圳"
      },
      {
        "name": "unit",
        "title": "温度单位",
        "inputType": "select",
        "defaultValue": "celsius",
        "options": [
          {
            "value": "celsius",
            "title": "摄氏度"
          },
          {
            "value": "fahrenheit",
            "title": "华氏度"
          }
        ]
      },
      {
        "name": "autoRefresh",
        "title": "自动刷新",
        "inputType": "switch",
        "defaultValue": true
      }
    ]
  }
]
```

## 4. 注册机制说明

系统启动时，`DesktopComponentServiceImpl` 会扫描所有已加载插件资源目录下的 `desktop-component.json`
文件，解析其中的组件定义并注册到组件库中。插件无需额外编写代码即可完成组件注册。

前端用户进入桌面编辑模式时，可从组件库中选择已注册的组件添加到个人或公共桌面中。
