# 开放接口列表

本文档列出了咸鱼云网盘开放平台的标准 OAuth 2.1 / OIDC 接口。

如果您接入的是标准 OIDC / OAuth 2.1 客户端，请优先阅读：[OIDC Provider 支持](../oidc.md)。

## 接口调用规范

### 1. 请求头要求

所有开放接口请求必须在请求头中包含标准 Bearer Token：

```http
Authorization: Bearer {access_token}
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


## 相关文档

- [OIDC Provider 支持](../oidc.md): 标准 OIDC / OAuth 2.1 端点说明
- [OAuth授权流程](../index.md): 完整的授权流程说明
