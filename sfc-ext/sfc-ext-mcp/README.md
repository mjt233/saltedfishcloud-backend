# mcp

## 简介

`sfc-ext-mcp` 是咸鱼云的 MCP 服务插件，基于 Spring AI MCP Server 提供面向 AI 客户端的网盘能力接入。

## 主要能力

- 通过 HTTP 提供 MCP 服务端点
- 使用系统现有 OAuth `ApiTicket` 完成鉴权
- 提供文件管理与用户信息相关工具

当前内置工具（`McpDiskTools`）包括：

- `list_files`
- `search_files`
- `delete_files`
- `copy_files`
- `move_files`
- `rename_file`
- `create_directory`
- `get_current_user_info`
- `get_user_info`
- `get_upload_file_method`
- `create_file_download_link`
- `get_download_file_method`

另外还提供一个 Prompt：

- `upload_file`（由 `McpDiskPrompt` 提供，用于指导 Agent 通过 HTTP 接口上传文件）

## 访问端点

- MCP HTTP Stream端点：`/api/mcp/stream`

## 鉴权方式

请求头示例：

```http
Authorization: ApiTicket <your_api_ticket>
```

需要先通过 前端页面 - 百宝箱 - MCP服务 进行OAuth授权获取`ApiTicket`

## 上传 与 下载说明

MCP 插件对上传/下载采用“工具返回方法说明 + 外部 HTTP 接口实际传输”的实现方式，而不是在 MCP 工具里直接传输二进制文件。

### 上传（特殊实现）

- 通过工具 `get_upload_file_method` 获取上传指引
- 或通过 Prompt `upload_file` 获取同类上传说明
- 实际上传目标为开放平台接口（默认）：`/api/openApi/diskFile/upload/v1`
- 上传使用 `multipart/form-data`，字段为：`uid`、`path`、`file`
- 鉴权头为：`Authorization: ApiTicket <your_api_ticket>`（ApiTicket在获取的Prompt中已包含，无需额外授权获取）

### 下载（特殊实现）

- 方式 1：调用 `create_file_download_link` 生成临时下载链接，再用普通 HTTP 下载
- 方式 2：调用 `get_download_file_method` 获取开放平台下载接口说明
- 默认开放平台下载接口：`/api/openApi/diskFile/download/v1`（GET，query 参数 `uid` 与 `path`）

### 设计原因

- MCP 工具更适合结构化元数据与操作编排
- 文件内容传输由现有开放平台 HTTP 接口承担，兼容性更好，且便于复用现有鉴权与下载链路
