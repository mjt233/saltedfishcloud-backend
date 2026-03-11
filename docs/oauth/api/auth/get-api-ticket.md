# 获取ApiTicket接口

## 接口说明

该接口用于第三方应用使用Access Token获取短期有效的ApiTicket。ApiTicket是访问咸鱼云网盘开放接口的凭证，有效期为15分钟。

**接口路径**: `GET /api/openApi/auth/getApiTicket/v1`

## 请求参数

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| `appId` | Long | 是 | 第三方应用ID | `123` |
| `uid` | Long | 是 | 用户ID | `456` |
| `accessToken` | String | 是 | Access Token | `s2w6KU-gxLSn8momzzG7mmnA5wQzToONi5OjZcuTXhM` |



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


## 使用ApiTicket调用开放接口

获取ApiTicket后，在调用咸鱼云网盘开放接口时，需要在请求头中添加Authentication头：

```http
GET /api/openApi/user/profile/v1
Authentication: ApiTicket eyJhbGciOiJIUzI1NiJ9...
```

## 注意事项

### 1. 有效期管理
- **ApiTicket有效期**: 15分钟
- **自动失效**: 每次获取新的ApiTicket时，旧的ApiTicket会立即失效
- **建议缓存时间**: 建议缓存14分钟，提前1分钟刷新

### 2. 安全性
- **不要暴露Access Token**: ApiTicket是短期凭证，相对安全，但Access Token必须严格保护
- **HTTPS要求**: 所有通信必须使用HTTPS
- **及时撤销**: 应用不再需要时，及时撤销授权


## 下一步

- [获取授权用户的信息](../user/profile.md): 获取授权用户的信息

## 相关接口

- [获取Access Token接口](get-access-token.md): 获取Access Token
- [OAuth授权流程](../../index.md): 完整的授权流程说明
- [开放接口列表](../index.md): 可用API接口列表