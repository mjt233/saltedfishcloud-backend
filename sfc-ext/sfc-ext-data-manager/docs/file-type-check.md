# 文件类型识别

## 概述

为未知类型文件提供类型检测与元数据提取

## 接口设计

为简化文档方便表示，文档中使用ts类型定义接口，实际实现需要使用Java：
```typescript
interface FileTypeCheckProvider {
    /**
     * 获取该Provider的唯一标识，如："imageCheckProvider"等，要求使用小写字母，且不包含空格。
     */
    getId(): string

    /**
     * 获取该Provider检测的文件类型名称，如："图片"、"视频"、"文档"等。
     * 一个Provider应只处理一类的文件
     */
    getTypeName(): string

    /**
     * 获取该Provider检测的文件类型标识，如："image"、"video"、"document"等。
     * 该标识用于内部区分不同类型的文件，使用小写字母，且不包含空格。
     */
    getTypeId(): string

    /**
     * 表示该Provider支持的文件拓展名列表，如：[".jpg", ".png", ".mp4", ".pdf"]等。
     * 该列表用于快速判断一个文件是否可能属于该类型，通常包含常见的文件后缀名。
     */
    getSupportedFileExtensions(): string[];

    /**
     * 获取该Provider支持提取的元数据定义列表
     */
    getMetadataDefines(): FileMetedataDefine[]

    /**
     * 对文件进行类型检测与元数据提取，返回结果或null表示无法识别
     * @param file    待检测的文件
     * @param extraMateData 是否包含提取元数据
     */
    checkFile(file: Resource, extraMateData: boolean): FileTypeCheckResultDetail | null;
}

/**
 * 根据注册的FileTypeCheckProvider，提供文件类型检测与元数据提取的接口
 */
interface FileTypeChecker {
    /**
     * 注册一个FileTypeCheckProvider
     * @param provider 要注册的FileTypeCheckProvider实例
     */
    addProvider(provider: FileTypeCheckProvider): void;

    /**
     * 获取已注册的FileTypeCheckProvider列表
     */
    getProviders(): List<FileTypeCheckProvider>;

    /**
     * 对文件进行类型检测与元数据提取
     * @param file 要检测的文件资源
     * @param extraMateData 是否提取额外的元数据
     * @returns 文件类型检测结果或null表示无法识别
     */
    checkFile(file: Resource, extraMateData: boolean): FileTypeCheckResult | null;
}

/**
 * 文件元数据定义
 */
interface FileMetedataDefine {
    name: string // 元数据的人类阅读友好名称，如"图片宽度"、"视频时长"等
    key: string // 元数据的唯一标识符，如"width"、"duration"等
    description?: string // 元数据的描述信息，说明该元数据的含义和用途
    viewTag?: string // 在页面视图上展示值时使用的html标签或vue组件名称，如"span"、"div"等，如果未定义则默认为"span"
}

interface FileTypeCheckResult {
    providerId: string // 提供该结果的FileTypeCheckProvider的id（与FileTypeCheckProvider#getId()一致）
    typeId: string // 文件类型标识，如"image"、"video"等，表示一大类文件（与FileTypeCheckProvider的getTypeId()一致）
    typeName: string // 文件类型名称，如"图片"、"视频"等，表示一大类文件（与FileTypeCheckProvider的getTypeName()一致）
    detail: FileTypeCheckResultDetail // 识别结果的详细信息，包括可能的拓展名、MIME类型、提取的元数据等
}

interface FileTypeCheckResultDetail {
    extension?: string // 文件可能的拓展名
    mimetype?: string // 文件的MIME类型，如"image/png"
    metadata?: Map<string, string>; // 提取的元数据，如{"width": "1024", "height": "768"}
    message?: string // 额外的提示信息，识别过程中遇到的问题或特殊情况等不影响识别，但需要告知用户留意的消息
}
```

## 与失效数据检测的集成

### 触发方式

文件识别通过异步任务执行，由管理员在失效数据检测完成后手动发起。

并发控制：限制只能同时有一个进行中的文件识别或检测任务，识别或识别过程中不允许再次发起检测或文件识别任务。

### 识别范围

识别任务处理所有失效数据记录中`是否为待识别文件`字段为true的记录：
- UNIQUE模式下的失效物理存储（无法从物理存储路径直接识别文件类型）
- RAW模式无需识别，直接根据拓展名识别即可

### 结果回写

识别完成后直接更新失效数据记录的以下字段，无需管理员二次确认（识别结果仅作参考信息）：
- `fileType`：基于系统支持的文件类型识别的typeId（如"image"、"video"、"document"等）
- `metadata`：提取的元数据（如图片宽高、视频时长等）
