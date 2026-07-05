**接口路径**: `POST /api/openApi/diskFile/move/v1`

**权限要求**: `storage_write`

**功能说明**: 将文件或目录移动到目标用户网盘的指定目录，支持跨用户移动。

**请求格式**: `application/json`

**请求体**:

| 字段名           | 类型       | 必填 | 说明                  |
|---------------|----------|----|---------------------|
| `sourceUid`   | number   | 否  | 源用户ID。0表示公共网盘       |
| `sourcePath`  | string   | 是  | 文件当前所在目录路径          |
| `targetUid`   | number   | 否  | 目标用户ID。0表示公共网盘      |
| `files`       | string[] | 是  | 待移动的文件名列表           |
| `targetPath`  | string   | 是  | 目标目录路径              |
| `isOverwrite` | boolean  | 否  | 是否覆盖同名文件，默认 `false` |

**请求参数** (Query 已弃用):

| 参数名   | 类型     | 必填 | 说明           |
|-------|--------|----|--------------|
| `uid` | number | 否  | 用户ID，仅作为兼容参数 |

**请求示例** (同一用户内移动):

```json
{
  "sourcePath": "/downloads",
  "files": [
    "report.pdf"
  ],
  "targetPath": "/documents",
  "isOverwrite": true
}
```

**请求示例** (跨用户移动):

```json
{
  "sourceUid": 1001,
  "sourcePath": "/downloads",
  "targetUid": 1002,
  "files": [
    "report.pdf"
  ],
  "targetPath": "/documents",
  "isOverwrite": true
}
```

**成功响应示例**:

```json
{
  "code": 200,
  "businessCode": 200,
  "msg": "OK",
  "data": null
}
```
