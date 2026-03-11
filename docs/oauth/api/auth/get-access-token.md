# 获取Access Token接口

## 接口说明

该接口用于第三方应用使用授权码换取Access Token。Access Token是90天有效的刷新令牌，用于获取短期有效的ApiTicket。

**接口路径**: `GET /api/openApi/auth/getAccessToken/v1`

## 请求参数

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| `code` | String | 是 | 授权码，从授权回调URL中获取 | `77afb8216bf847368c7e7aaf7b1224eb` |
| `clientSecret` | String | 是 | 第三方应用的客户端密钥 | `bd3a820b2636428d9e8653cf3ffb2cfe` |



## 响应格式

接口遵循统一的响应格式：

#### 成功响应示例
```json
{
    "code": 200,
    "businessCode": 200,
    "data": "s2w6KU-gxLSn8momzzG7mmnA5wQzToONi5OjZcuTXhM",
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
| businessCode | 说明 | HTTP状态码 |
|--------------|------|------------|
| `200` | 成功 | 200 |
| `60000` | 无效的token | 400 |
| `60001` | 无效的授权码code | 400 |
| `60002` | 无效的appId | 400 |
| `60003` | 应用已被停用 | 400 |
| `60004` | 未授权的操作 | 403 |
| `60005` | Client Secret验证失败 | 400 |

## 注意事项

### 1. 安全性

- **Client Secret保护**: Client Secret必须妥善保管，不应在客户端代码中暴露
- **HTTPS要求**: 所有请求必须使用HTTPS协议
- **授权码一次性**: 授权码只能使用一次，获取Access Token后立即失效

### 2. 有效期

- **授权码有效期**: 15分钟
- **Access Token有效期**: 90天

### 3. 错误处理

- 授权码过期或无效时，需要重新引导用户授权
- Client Secret错误时，检查应用配置
- 网络错误时，应有重试机制

## 下一步

- [获取ApiTicket接口](get-api-ticket.md): 使用Access Token获取ApiTicket

## 相关接口

- [OAuth授权流程](../../index.md): 完整的授权流程说明