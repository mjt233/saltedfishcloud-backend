# 咸鱼云网盘前端插件开发指南

本文档以 `sfc-ext-video-enhance`（视频增强插件）为例，介绍如何为咸鱼云网盘前端项目开发插件。

## 1. 插件命名规范

- 插件 ID 由小写字母和连字符组成，如 `video-enhance`、`web-shell`。
- 插件全名格式为 `sfc-ext-{插件id}`，如 `sfc-ext-video-enhance`。
- 插件目录、打包配置文件、`package.json` 中的命令均使用全名。

---

## 2. 创建插件代码目录

在项目根目录的 `sfc-ext/` 下创建插件代码目录，目录名为 `sfc-ext-{插件id}`。

### 2.1 推荐目录结构

```
sfc-ext/
└── sfc-ext-{插件id}/
    ├── main.ts           # 插件入口（必须）
    ├── api.ts            # 后端接口定义（可选）
    ├── model.ts          # TypeScript 类型/接口定义（可选）
    ├── package.json      # 插件私有依赖（可选）
    ├── core/             # 核心业务逻辑（可选）
    └── components/       # Vue 组件（可选）
```

### 2.2 编写 `main.ts` 入口文件

`main.ts` 是插件的初始化入口，在宿主应用加载插件时会执行此文件。插件通过全局变量 `window.context`、`window.SfcUtils`、
`window.API` 等访问宿主应用提供的能力。

以 `sfc-ext-video-enhance` 为例，入口文件完成以下工作：

```typescript
import {FileOpenHandler, FileInfo, FileListContext, getContext} from 'sfc-common'
import MyPlayerVue from './components/MyPlayer.vue'
import MyComponent from './components/MyComponent.vue'

const context = window.context
const SfcUtils = window.SfcUtils

// 注册文件打开处理器（"打开方式"）
const myOpenHandler: FileOpenHandler = {
    id: 'my-handler',
    icon: 'mdi-play-circle',
    title: '用我的插件打开',
    sort: 0,
    matcher(ctx: FileListContext, file: FileInfo) {
        // 返回 true 表示该文件可以被本插件处理
        return file.name.endsWith('.mp4')
    },
    async action(ctx: FileListContext, file: FileInfo) {
        SfcUtils.openComponentDialog(MyPlayerVue, {
            props: {file},
            title: '播放：' + file.name,
        })
    }
}

// 将处理器注册到全局上下文
context.fileOpenHandler.value.push(myOpenHandler)


// 注册自定义全局Vue组件
window.bootContext.addProcessor({
    taskName: '注册我的插件组件',
    execute(app, handler) {
        app.component('MyComponent', MyComponent)
    }
})
```

### 2.3 对宿主模块`sfc-common`的依赖导入

使用 `sfc-common` 中定义的类型和工具函数时，直接从模块导入即可，如

宿主模块 `sfc-common` 已在打包配置中设置为外部依赖（external），因此无需担心导入路径问题，直接使用模块名导入即可。
```typescript
import { getContext } from 'sfc-common'
```

> 注意：
> 
> 尽量避免通过`sfc-common/xxxx`这种形式导入`sfc-common`模块没有统一暴露的子模块，因为这会导致打包时无法正确外部化（externalize）依赖，进而引入冗余代码和潜在的版本冲突问题。
> ```typescript
> import { FileInfo } from 'sfc-common/model' // 不推荐，可能导致打包问题
> ```


宿主模块使用的第三方依赖可以使用正常方式直接import，如：`import qs from 'qs'`

宿主模块一些比较复杂的工具或对象被挂载到了全局的`window`对象下，详见以下声明
```typescript
import * as MethodInterceptor from 'sfc-common/utils/MethodInterceptor'
import DPlayer from 'dplayer'
import router from 'sfc-common/plugins/router'
import SfcUtils from 'sfc-common/utils/SfcUtils'
import { getContext } from '../context'
import * as Vue from 'vue'
import * as FormUtils from 'sfc-common/utils/FormUtils'
import API from 'sfc-common/api'
import { bootContext } from './BootCore'
import * as components from 'sfc-common/components'
import DOMUtils from 'sfc-common/utils/DOMUtils'
import { StringFormatter } from 'sfc-common/utils/StringFormatter'
import { StringUtils } from 'sfc-common/utils/StringUtils'
import * as monaco from 'monaco-editor'
import qs from 'qs'
import * as echarts from 'echarts'

window.context = getContext()
window.getContext = getContext
window.Vue = Vue
window.SfcUtils = SfcUtils
window.API = API
window.DPlayer = DPlayer as any
window.bootContext = bootContext
window.FormUtils = FormUtils
window.components = components
window.DOMUtils = DOMUtils
window.MethodInterceptor = MethodInterceptor
window.StringFormatter = StringFormatter
window.StringUtils = StringUtils
window.Components = components
window.echarts = echarts
window.qs = qs
window.monaco = monaco
```

模块`sfc-common`本身也被挂载到了`window.SfcCommon`

### 2.4 `package.json`（可选）

如果插件有私有 npm 依赖，需要在插件目录下创建 `package.json` 并执行 `npm install`：

```json
{
  "name": "sfc-ext-{插件id}",
  "version": "1.0.0",
  "private": true,
  "dependencies": {
    "some-lib": "^1.0.0"
  }
}
```

---

## 3. 开发模式：注册插件到 Vite 开发服务器

在 Vite 开发服务运行期间，插件通过 `.env.development` 中的 `VITE_PLUGINS` 变量被加载。

打开项目根目录的 `.env.development` 文件：

```dotenv
VITE_PLUGIN_PREFIX=sfc-ext
VITE_PLUGINS=demo,jntm,quick-share,video-enhance,network-tools,web-shell,static-publish,only-office,rtc,webdav
```

将你的 **插件 ID**（不含 `sfc-ext-` 前缀）追加到 `VITE_PLUGINS` 列表中，以逗号分隔：

```dotenv
VITE_PLUGIN_PREFIX=sfc-ext
VITE_PLUGINS=demo,jntm,quick-share,video-enhance,...,{插件id}
```

添加后，重启 Vite 开发服务（`npm run dev`），宿主应用会自动加载并执行 `sfc-ext/{插件id}/main.ts`。

---

## 4. 插件打包配置

### 4.1 创建打包配置文件

在 `build/extension/` 目录下创建 `{插件id}.ts`，内容固定调用 `defineExtension`：

```typescript
// build/extension/{插件id}.ts
import {defineExtension} from './build-template'

export default defineExtension({
    name: 'sfc-ext-{插件id}'
})
```

`defineExtension` 会自动配置：

| 配置项     | 说明                                                                   |
|---------|----------------------------------------------------------------------|
| 入口文件    | `sfc-ext/sfc-ext-{插件id}/main.ts`                                     |
| 输出目录    | `public/ext/sfc-ext-{插件id}/`                                         |
| 输出格式    | UMD，文件名为 `index.umd.js`                                              |
| 外部依赖    | `vue`、`vuetify`、`sfc-common`、`dplayer`、`monaco-editor`、`qs` 等不会打包进产物 |
| base 路径 | `/ext/sfc-ext-{插件id}/`                                               |

### 4.2 在 `package.json` 中添加打包命令

打开项目根目录的 `package.json`，在 `scripts` 中添加：

```json
{
  "scripts": {
    "build-ext-{插件id}": "vite build --config build/extension/{插件id}.ts"
  }
}
```

例如，`video-enhance` 插件对应的命令为：

```json
{
  "scripts": {
    "build-ext-video-enhance": "vite build --config build/extension/video-enhance.ts"
  }
}
```

### 4.3 执行打包

```bash
npm run build-ext-{插件id}
```

打包完成后，产物将输出到 `public/ext/sfc-ext-{插件id}/` 目录，主要文件为 `index.umd.js`。

---

## 5. 插件 API 参考

插件运行时可访问宿主应用注入的以下全局变量：

### `window.context`

全局应用上下文，类型为 `ToRefs<AppContext>`，主要字段：

| 字段                  | 类型                       | 说明                            |
|---------------------|--------------------------|-------------------------------|
| `fileOpenHandler`   | `Ref<FileOpenHandler[]>` | 文件打开方式列表，向其 `push` 即可注册新的打开方式 |
| `menu.mainMenu`     | `AppMenu[]`              | 主导航菜单，可插入自定义菜单项               |
| `menu.fileListMenu` | `MenuGroup[]`            | 文件列表右键菜单，可插入自定义操作             |
| `session`           | `Ref<Session>`           | 当前登录用户的会话信息                   |
| `feature`           | `Ref<SystemFeature>`     | 服务端功能特性开关                     |

### `window.SfcUtils`

宿主应用工具方法集，常用方法：

| 方法                                                 | 说明                                    |
|----------------------------------------------------|---------------------------------------|
| `SfcUtils.request(req)`                            | 发起 API 请求，返回 `Promise<AxiosResponse>` |
| `SfcUtils.openComponentDialog(component, options)` | 在对话框中打开一个 Vue 组件                      |
| `SfcUtils.snackbar(msg)`                           | 显示底部消息条                               |
| `SfcUtils.beginLoading()`                          | 显示全屏加载遮罩                              |
| `SfcUtils.closeLoading()`                          | 关闭全屏加载遮罩                              |
| `SfcUtils.sleep(ms)`                               | 等待指定毫秒数                               |

### `window.API`

后端接口定义集合，包含 `API.resource`、`API.file`、`API.user` 等命名空间，与 `sfc-common/api` 中的定义一致。

### `window.bootContext`

应用启动上下文，类型为 `BootContext`，主要方法：

| 方法                                    | 说明                                  |
|---------------------------------------|-------------------------------------|
| `bootContext.addProcessor(processor)` | 注册一个启动处理器，在 Vue 应用初始化时执行（用于全局注册组件等） |

### `sfc-common` 模块

插件可以从 `sfc-common` 导入类型和工具函数，该依赖在打包时会被外部化（不打包进产物）：

```typescript
import {FileOpenHandler, FileInfo, FileListContext, getContext, MenuHelper} from 'sfc-common'
```

---