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