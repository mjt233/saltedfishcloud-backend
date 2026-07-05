**接口路径**: `GET /api/openApi/diskFile/download/v1`

**权限要求**: `storage_read`

**功能说明**: 下载授权用户网盘指定路径的文件。

**请求参数**:

| 参数名 | 类型   | 必填 | 说明                            |
|--------|--------|------|--------------------------------|
| `uid`  | number | 是   | 目标用户ID，`0` 表示公共网盘   |
| `path` | string | 是   | 文件完整路径（包含文件名）     |

**成功响应**:

返回文件的二进制内容。响应头包含：

| 响应头          | 说明                                       |
|-----------------|------------------------------------------|
| `Content-Type`  | 文件的MIME类型，如 `image/jpeg`、`application/pdf` 等 |
| `Content-Disposition` | 打包为 `inline;filename*=UTF-8''<编码后的文件名>` |

**错误响应示例**:

文件不存在时返回：
```json
{
    "code": 400,
    "businessCode": 40001,
    "msg": "文件不存在",
    "data": null
}
```

无效路径时返回：
```json
{
    "code": 400,
    "businessCode": 40002,
    "msg": "无效的路径",
    "data": "当前值：/invalid/path"
}
```

**使用示例**:

```bash
# 下载公共网盘根目录的文件
curl -X GET "http://localhost:8080/api/openApi/diskFile/download/v1?uid=0&path=/document.pdf" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -o document.pdf

# 下载用户私人网盘子目录中的文件
curl -X GET "http://localhost:8080/api/openApi/diskFile/download/v1?uid=12345&path=/folder/subfolder/image.jpg" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -o image.jpg
```

**说明**: 

- 文件路径必须以 `/` 开头，且必须是有效的路径格式
- 如果指定用户对指定路径没有访问权限，则返回文件不存在错误
- 对于公共网盘（`uid=0`），所有认证用户都可以下载
- 对于私人网盘（`uid>0`），仅文件所有者或管理员可以下载

