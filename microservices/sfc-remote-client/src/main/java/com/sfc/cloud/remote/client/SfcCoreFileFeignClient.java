package com.sfc.cloud.remote.client;

import com.sfc.cloud.remote.constants.SfcServiceName;
import com.sfc.cloud.remote.model.SfcFileInfo;
import com.sfc.cloud.remote.model.SfcJsonResult;
import com.sfc.cloud.remote.model.SfcPageInfo;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * sfc-core 网盘文件操作客户端（Feign 声明式接口）
 *
 * <p>所有请求都落在 core 的内部接口前缀 {@link #PREFIX} 下，由
 * {@link SfcRemoteClientAutoConfiguration} 注册的内部调用凭证拦截器
 * （{@code com.sfc.cloud.auth.client.SfcInternalTokenFeignInterceptor}）逐请求签发 JWT 凭证。</p>
 *
 * @see SfcCoreFileClient 对外的统一调用入口
 */
@FeignClient(
        name = SfcServiceName.CORE,
        contextId = "sfcCoreFileClient",
        path = SfcCoreFileFeignClient.PREFIX
)
public interface SfcCoreFileFeignClient {

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
     * 搜索文件。
     *
     * <p>在目标用户网盘内按文件名进行全盘模糊匹配，不限定目录范围。</p>
     *
     * @param uid     资源所属用户ID，0 表示公共网盘
     * @param keyword 搜索关键字，不能为空
     * @param page    页码，从 0 开始
     * @param size    每页数量，取值 1~100，超出上限时服务端按 100 处理
     * @return 分页搜索结果
     */
    @GetMapping("search")
    SfcJsonResult<SfcPageInfo<SfcFileInfo>> search(@RequestParam("uid") long uid,
                                                   @RequestParam("keyword") String keyword,
                                                   @RequestParam("page") int page,
                                                   @RequestParam("size") int size);

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
