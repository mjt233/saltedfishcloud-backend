# OAuth开放平台

咸鱼云网盘提供OAuth 2.0开放平台授权机制，允许第三方应用通过咸鱼云网盘进行用户身份验证和授权访问。

## 概述

咸鱼云网盘开放平台采用OAuth 2.0授权码模式，支持第三方应用获取用户授权后访问咸鱼云网盘的API接口。整个授权流程分为以下几个步骤：

1. 用户授权：第三方应用引导用户跳转到咸鱼云网盘授权页面
2. 获取授权码：用户确认授权后，咸鱼云网盘返回授权码
3. 获取Access Token：第三方应用使用授权码换取Access Token
4. 获取ApiTicket：使用Access Token获取短期有效的API访问凭证
5. 调用API：使用ApiTicket访问咸鱼云网盘开放接口

## 授权范围

咸鱼云网盘开放平台支持以下授权范围：

| 范围 | 说明 | 权限说明 |
|------|------|----------|
| `profile` | 个人信息 | 获取用户基本信息，如用户名、邮箱等 |
| `storage_read` | 存储读取权限 | 读取用户的私人网盘文件和数据 |
| `storage_write` | 存储写入权限 | 修改用户的私人网盘文件和数据 |

**注意**：

- 多个授权范围使用空格分隔，例如：`profile storage_read`

## 快速开始

### 1. 创建第三方应用

在开始集成之前，您需要在咸鱼云网盘管理员后台创建您的第三方OAuth应用，并获取以下信息：

- **App ID**: 应用唯一标识
- **Client Secret**: 应用密钥，用于授权接口安全验证

### 2. 实现授权流程

以下是完整的授权流程示意图：

```mermaid
sequenceDiagram
    title 咸鱼云网盘OAuth授权流程
    participant User as 用户
    participant ThirdPartyApp as 第三方应用
    participant SaltedFishCloud as 咸鱼云网盘
    
    User->>ThirdPartyApp: 访问第三方应用
    ThirdPartyApp->>User: 重定向到咸鱼云授权页面
    User->>SaltedFishCloud: 访问/oauth?appId=xxx&scope=xxx
    SaltedFishCloud->>User: 显示授权确认页面
    User->>SaltedFishCloud: 确认授权
    SaltedFishCloud->>ThirdPartyApp: 重定向用户到Callback URL with code
    ThirdPartyApp->>SaltedFishCloud: GET /api/openApi/auth/getAccessToken/v1
    SaltedFishCloud-->>ThirdPartyApp: 返回Access Token
    ThirdPartyApp->>SaltedFishCloud: GET /api/openApi/auth/getApiTicket/v1
    SaltedFishCloud-->>ThirdPartyApp: 返回ApiTicket
    ThirdPartyApp->>SaltedFishCloud: 使用ApiTicket调用开放接口
    SaltedFishCloud-->>ThirdPartyApp: 返回API响应
```


### 3. 详细步骤说明

#### 步骤1：引导用户授权

第三方应用需要引导用户访问咸鱼云网盘的授权页面，URL格式如下：

```
https://your-saltedfishcloud-domain/oauth?appId={appId}&scope={scope}
```

**参数说明**：

- `appId`: 第三方应用的唯一标识（必填）
- `scope`: 授权范围，多个范围用空格分隔（必填）

**示例**：
```
https://cloud.example.com/oauth?appId=123&scope=profile storage_read
```

#### 步骤2：处理授权回调

用户确认授权后，咸鱼云网盘会将页面重定向到第三方应用配置的回调URL，并在URL中添加授权码参数：

```
https://your-callback-domain/callback?code={authorization_code}
```

**参数说明**：

- `code`: 授权码，用于换取Access Token（有效期10分钟）

#### 步骤3：获取Access Token

使用授权码和Client Secret请求获取Access Token：

```bash
GET /api/openApi/auth/getAccessToken/v1?code={code}&clientSecret={clientSecret}
```

**接口详情**：参见[获取Access Token接口文档](api/auth/get-access-token.md)

#### 步骤4：获取ApiTicket

使用Access Token、App ID和用户ID获取ApiTicket：

```bash
GET /api/openApi/auth/getApiTicket/v1?appId={appId}&uid={uid}&accessToken={accessToken}
```

**接口详情**：参见[获取ApiTicket接口文档](api/auth/get-api-ticket.md)


#### 步骤5：调用开放接口

在调用咸鱼云网盘开放接口时，需要在请求头中添加ApiTicket：

```
Authentication: ApiTicket {api_ticket}
```

## 安全注意事项

1. **Client Secret保护**：Client Secret是应用的核心机密，必须妥善保管，不应在客户端代码中暴露
2. **HTTPS要求**：所有通信必须使用HTTPS协议，确保数据传输安全
3. **授权码有效期**：授权码有效期为15分钟，获取后应立即使用
4. **ApiTicket有效期**：ApiTicket有效期为15分钟，过期后需要重新获取
5. **权限最小化**：只请求应用实际需要的权限范围
6. **错误处理**：妥善处理各种错误情况，如授权被拒绝、token过期等

## 常见问题

### Q: 如何获取App ID和Client Secret？
A: 需要管理员在咸鱼云网盘管理员后台创建第三方OAuth应用，并创建应用的密钥

### Q: Access Token会过期吗？
A: Access Token有效期为90天，如果用户主动撤销授权或重新走了授权流程则会让Access Token提前失效。

### Q: 可以同时获取多个授权范围吗？
A: 可以，在scope参数中使用空格分隔多个范围，如：`profile storage_read storage_write`。

### Q: 如何撤销授权？
A: 用户可以在咸鱼云网盘的个人中心 - 第三方应用授权 中撤销授权

## 下一步

- [获取Access Token接口文档](api/auth/get-access-token.md)
- [获取ApiTicket接口文档](api/auth/get-api-ticket.md)
- [开放接口列表](api/index.md)