package com.sfc.cloud.remote.client;

import com.sfc.cloud.model.SfcFileInfo;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.model.SfcPageInfo;
import feign.Response;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Objects;

/**
 * sfc-core 网盘文件操作统一客户端
 */
public class SfcCoreFileClient {

    /** Feign 链路实现：列表 / 搜索 / 下载 / 创建目录 / 重命名 / 复制 / 移动 / 删除 */
    private final SfcCoreFileFeignClient feignClient;

    /** 流式链路实现：上传 */
    private final SfcCoreStreamFileClient streamClient;

    /**
     * 构造统一客户端，由自动装配负责组装（包内可见：消费方不应自行构造）。
     *
     * @param feignClient  Feign 链路实现
     * @param streamClient 流式上传链路实现
     */
    SfcCoreFileClient(SfcCoreFileFeignClient feignClient, SfcCoreStreamFileClient streamClient) {
        this.feignClient = Objects.requireNonNull(feignClient, "feignClient 不能为 null");
        this.streamClient = Objects.requireNonNull(streamClient, "streamClient 不能为 null");
    }

    /**
     * 获取文件列表。
     *
     * @param uid  资源所属用户ID，0 表示公共网盘
     * @param path 要列出的目录路径
     * @return 目录下的文件与目录列表（目录在前）
     */
    public SfcJsonResult<List<SfcFileInfo>> list(long uid, String path) {
        return feignClient.list(uid, path);
    }

    /**
     * 搜索文件。
     *
     * <p>在目标用户网盘内按文件名进行全盘模糊匹配，不限定目录范围。分页结果中的路径信息见
     * {@link SfcFileInfo#getParent()}（上级目录名称）。</p>
     *
     * <p>权限沿用 core 的 UID 校验规则：{@code uid=0}（公共网盘）允许匿名调用；其它用户网盘
     * 要求调用链路上带有该用户登录态（或管理员）的凭证，否则 core 返回 403。</p>
     *
     * @param uid     资源所属用户ID，0 表示公共网盘
     * @param keyword 搜索关键字，不能为空
     * @param page    页码，从 0 开始
     * @param size    每页数量，取值 1~100，超出上限时服务端按 100 处理
     * @return 分页搜索结果
     */
    public SfcJsonResult<SfcPageInfo<SfcFileInfo>> search(long uid, String keyword, int page, int size) {
        return feignClient.search(uid, keyword, page, size);
    }

    /**
     * 下载文件。
     *
     * <p>返回的是原始 {@link Response}（与底层契约 1:1），调用方负责消费并关闭响应体；内容仍会整体读入内存，
     * 真流式下载通道见路线图阶段 6.4。</p>
     *
     * @param uid  资源所属用户ID，0 表示公共网盘
     * @param path 文件路径，需要包含文件名本身，如 "/docs/a.txt"
     * @return 文件资源
     */
    public Response download(long uid, String path) {
        return feignClient.download(uid, path);
    }

    /**
     * 重命名文件。
     *
     * @param uid     资源所属用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path    被重命名对象所在的目录路径，不含其名称本身，根目录为 "/"
     * @param oldName 原名称
     * @param newName 新名称
     * @return core 的响应信封（含执行结果状态码）
     */
    public SfcJsonResult<Void> rename(long uid, String path, String oldName, String newName) {
        return feignClient.rename(uid, path, oldName, newName);
    }

    /**
     * 创建目录。
     *
     * <p>目标按「父路径 + 名称」定位，父级目录不存在时由 core 连同父级一并创建
     * （等价于 {@code mkdir -p}），因此无需先确认父目录存在。</p>
     *
     * <p>权限沿用 core 的 UID 校验规则：{@code uid=0}（公共网盘）要求调用链路上带有管理员
     * 权限的凭证，写入其它用户网盘则要求该用户本人（或管理员）的凭证，否则 core 返回 403。</p>
     *
     * @param uid  资源所属用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path 新目录所在的父路径，根目录为 "/"
     * @param name 新目录名称，只含名称本身，不含目录
     * @return core 的响应信封（含执行结果状态码）
     */
    public SfcJsonResult<Void> mkdirs(long uid, String path, String name) {
        return feignClient.mkdirs(uid, path, name);
    }

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
     * @return core 的响应信封（含执行结果状态码）
     */
    public SfcJsonResult<Void> copy(long uid, long sourceUid, long targetUid, String sourcePath,
                                    String targetPath, boolean overwrite, List<String> names) {
        return feignClient.copy(uid, sourceUid, targetUid, sourcePath, targetPath, overwrite, names);
    }

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
     * @return core 的响应信封（含执行结果状态码）
     */
    public SfcJsonResult<Void> move(long uid, long sourceUid, long targetUid, String sourcePath,
                                    String targetPath, boolean overwrite, List<String> names) {
        return feignClient.move(uid, sourceUid, targetUid, sourcePath, targetPath, overwrite, names);
    }

    /**
     * 删除文件。
     *
     * @param uid   资源所属用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path  文件所在目录路径，不含文件名，根目录为 "/"
     * @param names 要删除的文件名列表，只含名称本身，不含目录
     * @return core 的响应信封（含执行结果状态码）
     */
    public SfcJsonResult<Void> delete(long uid, String path, List<String> names) {
        return feignClient.delete(uid, path, names);
    }

    /**
     * 以流式方式上传文件到 core。
     *
     * <p>文件内容从 {@code file} 边读边写，调用方需保证在上传期间该资源可读；上传完成后本方法不会关闭它。</p>
     *
     * @param file  文件资源，建议使用文件型资源（{@code FileSystemResource}，支持边读边发）；
     *              使用无文件名的资源（如 {@code InputStreamResource}、{@code ByteArrayResource}）时请显式传 {@code name}
     * @param uid   保存到的用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path  保存到的目录路径，不含文件名，根目录为 "/"
     * @param name  保存的文件名，为 null 时使用资源的文件名
     * @param mtime 文件修改时间（毫秒时间戳），为 null 时由服务端决定
     * @return core 的响应信封（含保存结果状态码）；失败时抛 {@code SfcRemoteApiException}
     */
    public SfcJsonResult<Long> upload(Resource file, long uid, String path, String name, Long mtime) {
        return streamClient.upload(file, uid, path, name, mtime);
    }
}
