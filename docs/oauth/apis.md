# 开放接口列表

本文档列出了咸鱼云网盘开放平台提供的API接口。所有接口都需要使用ApiTicket进行身份验证。

## 接口调用规范

### 1. 请求头要求

所有开放接口请求必须在请求头中包含ApiTicket：

```http
Authentication: ApiTicket {api_ticket}
Content-Type: application/json
```

### 2. 响应格式

所有接口都遵循统一的响应格式：

```json
{
    "code": 200, // HTTP状态码，通常与HTTP响应状态码一致
    "businessCode": 200, // 业务代码，详见下方说明
    "data": {...}, // 获取到的业务数据，成功时返回具体数据，失败时为null
    "msg": "OK" // 消息提示，成功时为"OK"，失败时为错误描述
}
```

#### 成功响应示例
```json
{
    "code": 200,
    "businessCode": 200,
    "data": {
        "id": 123,
        "username": "testuser",
        "email": "test@example.com"
    },
    "msg": "OK"
}
```

#### 错误响应示例
```json
{
    "code": 400,
    "businessCode": 60001,
    "data": null,
    "msg": "无效的授权码code"
}
```

#### businessCode说明
所有开放平台接口使用统一的业务错误码：

| businessCode | 说明 | HTTP状态码 |
|--------------|------|------------|
| `200` | 成功 | 200 |
| `60000` | 无效的token | 400 |
| `60001` | 无效的授权码code | 400 |
| `60002` | 无效的appId | 400 |
| `60003` | 应用已被停用 | 400 |
| `60004` | 未授权的操作 | 403 |
| `60005` | Client Secret验证失败 | 400 |

## 用户相关接口

### 获取用户基本信息

**接口路径**: `GET /api/openApi/user/profile/v1`

**权限要求**: `profile`

**功能说明**: 获取授权用户的基本信息

**请求参数**: 无

**成功响应示例**: 
```json
{
    "data": {
        "id": "1054223881344122880",
        "username": "admin",
        "email": "",
        "avatar": "http://127.0.0.1:8087/api/user/avatar/admin?uid=1054223881344122880"
    },
    "msg": "OK",
    "code": 200,
    "businessCode": 200
}
```

## 相关文档

- [OAuth授权流程](index.md): 完整的授权流程说明
- [获取Access Token接口](get-access-token.md): 获取Access Token
- [获取ApiTicket接口](get-api-ticket.md): 获取ApiTicket