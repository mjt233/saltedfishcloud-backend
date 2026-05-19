# sfc-ext-mcp

基于 Spring AI MCP Server 的咸鱼云 MCP 插件。

## 功能

- 基于 HTTP 的 MCP Server 接入
- 基于系统现有 OAuth `ApiTicket` 的鉴权
- 支持以下工具：
  - `list_files`
  - `delete_files`
  - `copy_files`
  - `move_files`
  - `get_current_user_info`
  - `get_user_info`
  - `upload_file`
  - `quick_save_file`
  - `rename_file`
  - `create_directory`

## 上传说明

- `upload_file`：通过 Base64 传输文件内容，适合小文件
- `quick_save_file`：通过 MD5 秒传保存文件，不传输文件内容，适合大文件或系统中已存在相同文件的场景

## 访问地址

- MCP 消息端点：`/api/mcp/message`
- SSE 端点：`/api/mcp/sse`

## 认证方式

请求头携带：

```http
Authorization: ApiTicket <your_api_ticket>
```

## 开发验证

```powershell
cd C:\Users\xiaotao\code\saltedfishcloud-backend
mvn compile -pl sfc-ext/sfc-ext-mcp -am
mvn package -pl sfc-ext/sfc-ext-mcp -am -DskipTests
```

