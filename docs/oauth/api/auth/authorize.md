# 授权确认接口

## 接口说明

该接口用于已登录用户在咸鱼云网盘授权确认页面点击“确认授权”后，向第三方应用签发授权码。

接口支持两种响应模式：

1. 直接返回 `302` 跳转到最终回调地址（默认行为）
2. 返回包含授权码和最终回调地址的JSON数据，供调用方自行处理跳转

**接口路径**: `GET /api/oauth/authorize`

## 前置条件

- 当前用户必须已登录
- 当前用户需要在授权确认页面中同意授权
- `appId` 对应的第三方应用必须存在且处于启用状态

## 请求参数

| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| `appId` | Long | 是 | 第三方应用ID | `123` |
| `scope` | String | 是 | 授权范围，多个范围使用空格分隔 | `profile storage_read` |
| `redirect` | Boolean | 否 | 是否直接返回302跳转响应，默认 `true` | `false` |
| `redirectUrl` | String | 否 | 自定义回调地址 | `https://client.example.com/oauth/callback` |

## 回调地址判定规则

### 1. 应用已配置回调URL

- 未传 `redirectUrl`：使用应用已配置的回调URL
- 传入 `redirectUrl` 且与应用已配置回调URL一致：允许请求
- 传入 `redirectUrl` 且与应用已配置回调URL不一致：拒绝请求

### 2. 应用未配置回调URL

- 必须传入 `redirectUrl`
- 最终会以传入的 `redirectUrl` 作为回调地址

## 响应格式

### 1. `redirect=true` 时

接口返回 `302 Found`，并通过 `Location` 响应头跳转到最终回调地址：

```http
HTTP/1.1 302 Found
Location: https://client.example.com/oauth/callback?code=77afb8216bf847368c7e7aaf7b1224eb
```

### 2. `redirect=false` 时

接口返回统一JSON结构：

```json
{
    "code": 200,
    "businessCode": 200,
    "data": {
        "code": "77afb8216bf847368c7e7aaf7b1224eb",
        "redirectUrl": "https://client.example.com/oauth/callback?code=77afb8216bf847368c7e7aaf7b1224eb"
    },
    "msg": "OK"
}
```

## 使用建议

- 浏览器页面授权场景建议保持默认 `redirect=true`
- 如果你需要先记录日志、展示中间页或由后端统一处理跳转，可以使用 `redirect=false`
- 如果第三方应用没有预配置回调URL，务必在请求时传入 `redirectUrl`
- 如果第三方应用已配置回调URL，建议仍由服务端进行一致性校验，避免回调地址被篡改

## 相关接口

- [获取Access Token接口](get-access-token.md): 使用授权码换取Access Token
- [获取ApiTicket接口](get-api-ticket.md): 使用Access Token获取ApiTicket
- [OAuth授权流程](../../index.md): 完整的授权流程说明

