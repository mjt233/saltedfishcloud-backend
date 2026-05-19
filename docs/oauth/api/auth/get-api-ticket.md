# 获取ApiTicket接口

## 接口说明

该接口用于第三方应用使用 Access Token 获取 ApiTicket。ApiTicket 是访问咸鱼云网盘开放接口的凭证，默认签发 15 分钟有效的临时票据；对已开启能力的第三方应用，也支持申请永久有效的 ApiTicket。

**接口路径**: `GET /api/openApi/auth/getApiTicket/v1`

## 请求参数

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| `accessToken` | String | 是 | Access Token | `s2w6KU-gxLSn8momzzG7mmnA5wQzToONi5OjZcuTXhM` |
| `permanent` | Boolean | 否 | 是否申请永久有效的ApiTicket，默认 `false` | `true` |



## 响应格式

接口遵循统一的响应格式：

#### 成功响应示例
```json
{
    "code": 200,
    "businessCode": 200,
    "data": "eyJhbGciOiJIUzI1NiJ9.eyJkYXRhIjoie1wiYXBwSWRcIjpcIjEyMjY5NzMyMzkzNjQ4MTI4MDBcIixcInVpZFwiOlwiMTA1NDIyMzg4MTM0NDEyMjg4MFwiLFwic2NvcGVcIjpcIiBwcm9maWxlIHN0b3JhZ2VfcmVhZCBzdG9yYWdlX3JlYWRfd3JpdGVcIixcImp0aVwiOlwiMTIyOTI1OTgwNzU1MTEyNzU1MlwifSIsImV4cCI6MTc3MzI0NTc0MiwiaWF0IjoxNzczMjQ0ODQyfQ.btAVlCUSop-ie5rBPqg9lj_2fbIKHyfWcHbTTZ8DzHk",
    "msg": "OK"
}
```

#### 错误响应示例
```json
{
    "code": 400,
    "businessCode": 60000,
    "data": null,
    "msg": "无效的token"
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
| `60006` | 当前应用不允许申请永久ApiTicket | 400 |


## 使用ApiTicket调用开放接口

获取ApiTicket后，在调用咸鱼云网盘开放接口时，需要在请求头中添加Authorization头：

```http
GET /api/openApi/user/profile/v1
Authorization: ApiTicket eyJhbGciOiJIUzI1NiJ9...
```

## 注意事项

### 1. 有效期管理
- **临时ApiTicket有效期**: 15分钟
- **永久ApiTicket有效期**: 不设置JWT过期时间，仅允许已开启永久ApiTicket能力的应用申请
- **自动失效**: 每次获取新的同类型 ApiTicket 时，旧的同类型 ApiTicket 会立即失效
- **撤销授权失效**: 用户撤销授权后，该应用下该用户的所有ApiTicket（包含永久ApiTicket）都会立即失效
- **建议缓存时间**: 临时ApiTicket建议缓存14分钟，提前1分钟刷新；永久ApiTicket也应在收到401/400无效token后及时重新申请

### 2. 安全性
- **不要暴露Access Token**: ApiTicket是短期凭证，相对安全，但Access Token必须严格保护
- **HTTPS要求**: 所有通信必须使用HTTPS
- **及时撤销**: 应用不再需要时，及时撤销授权
- **永久ApiTicket控制**: 仅在服务端环境且具备长期持有凭据能力的场景下申请永久ApiTicket


## 下一步

- [获取授权用户的信息](../user/profile.md): 获取授权用户的信息

## 相关接口

- [获取Access Token接口](get-access-token.md): 获取Access Token
- [授权确认接口](authorize.md): 用户确认授权并获取授权码
- [OAuth授权流程](../../index.md): 完整的授权流程说明
- [开放接口列表](../index.md): 可用API接口列表