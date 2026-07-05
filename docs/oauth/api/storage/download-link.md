**接口路径**: `GET /api/openApi/diskFile/downloadLink/v1`

**权限要求**: `storage_read`

**功能说明**: 为网盘中的受保护文件创建临时下载链接。返回的链接在有效期内可直接访问，无需再次鉴权。

**请求参数**:

| 参数名 | 类型   | 必填 | 说明                            |
|--------|--------|------|--------------------------------|
| `uid`  | number | 是   | 目标用户ID，`0` 表示公共网盘   |
| `path` | string | 是   | 文件完整路径（包含文件名）     |

**成功响应**:

```json
{
    "code": 200,
    "msg": "success",
    "data": "http://localhost:8080/api/fileLink/download?token=AbcDef12"
}
```

**错误响应示例**:

文件不存在或当前调用方无权访问目标文件时返回：
```json
{
    "code": 400,
    "businessCode": 40001,
    "msg": "文件不存在",
    "data": null
}
```

**使用示例**:

```bash
# 创建临时下载链接
curl -X GET "http://localhost:8080/api/openApi/diskFile/downloadLink/v1?uid=12345&path=/folder/report.pdf" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# 直接访问返回的临时链接下载文件（无需再带鉴权头）
curl -X GET "http://localhost:8080/api/fileLink/download?token=AbcDef12" \
  -o report.pdf
```

**说明**:

- 临时授权码有效期由系统配置 `sys.common.file-link-expire-minutes` 控制，默认 `10` 分钟
- 该接口只负责生成链接，不会直接返回文件内容
- 临时链接过期或授权码无效时，访问下载链接会返回“链接无效或已过期”错误

