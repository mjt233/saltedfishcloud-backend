package com.sfc.cloud.remote.client;

import com.sfc.cloud.remote.constants.SfcServiceName;
import com.sfc.cloud.remote.model.SfcFileInfo;
import com.sfc.cloud.remote.model.SfcJsonResult;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * sfc-core 网盘文件操作客户端。
 */
@FeignClient(
        name = SfcServiceName.CORE,
        contextId = "sfcCoreFileClient",
        path = SfcCoreFileClient.PREFIX
)
public interface SfcCoreFileClient {

    /** 接口前缀 */
    String PREFIX = "/api/internal/file/v2";

    /**
     * 获取文件列表。
     *
     * @param uid  资源所属用户ID，0 表示公共网盘
     * @param path 要列出的目录路径
     * @return 目录下的文件与目录列表（目录在前）
     */
    @GetMapping("list")
    SfcJsonResult<List<SfcFileInfo>> list(@RequestParam("uid") long uid,
                                          @RequestParam("path") String path);

    /**
     * 下载文件。
     *
     * @param uid  资源所属用户ID，0 表示公共网盘
     * @param path 文件路径，需要包含文件名本身，如 "/docs/a.txt"
     * @return 文件资源
     */
    @GetMapping("download")
    Response download(@RequestParam("uid") long uid,
                      @RequestParam("path") String path);

    /**
     * 上传文件。
     *
     * @param file  上传的文件
     * @param uid   保存到的用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path  保存到的目录路径，不含文件名，根目录为 "/"
     * @param name  保存的文件名，为 null 时使用上传文件的原始文件名
     * @param mtime 文件修改时间（毫秒时间戳），为 null 时由服务端决定
     * @return 保存结果状态码
     */
    @PostMapping(value = "upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    SfcJsonResult<Long> uploadFile(@RequestPart("file") InputStreamSource file,
                                   @RequestPart("uid") long uid,
                                   @RequestPart("path") String path,
                                   @RequestPart(value = "name", required = false) String name,
                                   @RequestPart(value = "mtime", required = false) Long mtime);

    /**
     * 上传文件。
     *
     * @param content 文件内容字节
     * @param name    保存的文件名，不能为 null（字节内容无法提供文件名）
     * @param uid     保存到的用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path    保存到的目录路径，不含文件名，根目录为 "/"
     * @param mtime   文件修改时间（毫秒时间戳），为 null 时由服务端决定
     * @return 保存结果状态码
     */
    @PostMapping(value = "upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    SfcJsonResult<Long> uploadBytes(@RequestPart("file") byte[] content,
                                    @RequestPart("name") String name,
                                    @RequestPart("uid") long uid,
                                    @RequestPart("path") String path,
                                    @RequestPart(value = "mtime", required = false) Long mtime);

    /**
     * 重命名文件。
     *
     * @param uid     资源所属用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path    被重命名对象所在的目录路径，不含其名称本身，根目录为 "/"
     * @param oldName 原名称
     * @param newName 新名称
     */
    @PostMapping("rename")
    SfcJsonResult<Void> rename(@RequestParam("uid") long uid,
                               @RequestParam("path") String path,
                               @RequestParam("oldName") String oldName,
                               @RequestParam("newName") String newName);

    /**
     * 复制文件。
     *
     * @param uid        当前操作用户ID
     * @param sourceUid  源文件所属用户ID
     * @param targetUid  目标用户ID，为 0（公共网盘）时需要管理员权限
     * @param sourcePath 源目录路径，不含文件名，根目录为 "/"
     * @param targetPath 目标目录路径，不含文件名，根目录为 "/"
     * @param overwrite  目标存在同名文件时是否覆盖
     * @param names      要复制的文件名列表，只含名称本身，不含目录
     */
    @PostMapping("copy")
    SfcJsonResult<Void> copy(@RequestParam("uid") long uid,
                             @RequestParam("sourceUid") long sourceUid,
                             @RequestParam("targetUid") long targetUid,
                             @RequestParam("sourcePath") String sourcePath,
                             @RequestParam("targetPath") String targetPath,
                             @RequestParam("overwrite") boolean overwrite,
                             @RequestBody List<String> names);

    /**
     * 移动文件。
     *
     * @param uid        当前操作用户ID
     * @param sourceUid  源文件所属用户ID
     * @param targetUid  目标用户ID，为 0（公共网盘）时需要管理员权限
     * @param sourcePath 源目录路径，不含文件名，根目录为 "/"
     * @param targetPath 目标目录路径，不含文件名，根目录为 "/"
     * @param overwrite  目标存在同名文件时是否覆盖
     * @param names      要移动的文件名列表，只含名称本身，不含目录
     */
    @PostMapping("move")
    SfcJsonResult<Void> move(@RequestParam("uid") long uid,
                              @RequestParam("sourceUid") long sourceUid,
                              @RequestParam("targetUid") long targetUid,
                              @RequestParam("sourcePath") String sourcePath,
                              @RequestParam("targetPath") String targetPath,
                              @RequestParam("overwrite") boolean overwrite,
                              @RequestBody List<String> names);

    /**
     * 删除文件。
     *
     * @param uid   资源所属用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path  文件所在目录路径，不含文件名，根目录为 "/"
     * @param names 要删除的文件名列表，只含名称本身，不含目录
     */
    @PostMapping("delete")
    SfcJsonResult<Void> delete(@RequestParam("uid") long uid,
                               @RequestParam("path") String path,
                               @RequestBody List<String> names);
}
