package com.xiaotao.saltedfishcloud.controller.internal;

import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.SfcPageInfo;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcStringUtils;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import com.xiaotao.saltedfishcloud.validator.annotations.ValidPath;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 网盘文件操作 V2 控制器。仅用于微服务系统内部调用。
 *
 * <p>{@value #PREFIX} 属于 {@code /api/internal/**} 前缀，由 {@code sfc-auth} SDK 的
 * {@code SfcInternalAuthFilter} 强制校验服务间调用凭证（请求头 {@code X-Sfc-Internal-Token} 中
 * 每次调用新签发的 HS256 JWT），客户端无法直接访问；网关侧也会直接拒绝该前缀。
 * 因此这里的 {@code @AllowAnonymous} 表达的是"调用方是服务、没有用户登录态"，而不是"对所有人开放"。</p>
 */
@RestController
@RequestMapping(FileV2Controller.PREFIX)
@Validated
@ProtectBlock
@RequiredArgsConstructor
@Tag(name = "网盘文件操作-sfc-core微服务间内部接口")
public class FileV2Controller {

    /** 接口前缀 */
    public static final String PREFIX = "/api/internal/file/v2";

    /** 单次搜索允许的最大分页大小，避免内部接口被超大分页请求拖垮 */
    public static final int MAX_SEARCH_SIZE = 100;

    /** 文件系统管理器 */
    private final DiskFileSystemManager diskFileSystemManager;

    /** 获取文件列表 */
    @GetMapping("list")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<List<FileInfo>> list(@RequestParam("uid") long uid,
                                           @RequestParam(value = "path", defaultValue = "/") @ValidPath String path) throws IOException {
        List<FileInfo>[] fileList = diskFileSystemManager.getMainFileSystem().getUserFileList(uid, path);
        List<FileInfo> merged = new ArrayList<>();
        if (fileList != null) {
            for (List<FileInfo> group : fileList) {
                if (group != null) {
                    merged.addAll(group);
                }
            }
        }
        return SfcJsonResult.getInstance(merged);
    }

    /**
     * 搜索文件。
     *
     * <p>在目标用户的网盘内按文件名进行全盘模糊匹配，不限定目录范围；关键字中的空白字符会被视为
     * 词间分隔（与 Web 端搜索一致），{@code %} 会被剔除以免影响 LIKE 语义。</p>
     *
     * @param uid     资源所属用户ID，0 表示公共网盘
     * @param keyword 搜索关键字，不能为空
     * @param page    页码，从 0 开始，负数按 0 处理
     * @param size    每页数量，取值 1~{@value #MAX_SEARCH_SIZE}，超出上限时按上限处理
     * @return 分页搜索结果
     */
    @GetMapping("search")
    @AllowAnonymous
    @NotBlock
    public SfcJsonResult<SfcPageInfo<FileInfo>> search(@RequestParam("uid") long uid,
                                                      @RequestParam("keyword") String keyword,
                                                      @RequestParam(value = "page", defaultValue = "0") Integer page,
                                                      @RequestParam(value = "size", defaultValue = "10") Integer size) {
        if (!SfcStringUtils.hasText(keyword)) {
            throw new SfcJsonException(400, "搜索关键字不能为空");
        }
        int pageIndex = Math.max(page == null ? 0 : page, 0);
        int pageSize = Math.min(Math.max(size == null ? 10 : size, 1), MAX_SEARCH_SIZE);
        return SfcJsonResult.getInstance(
                diskFileSystemManager.getMainFileSystem().search(uid, keyword.trim(), pageIndex, pageSize));
    }

    /** 下载文件 */
    @GetMapping("download")
    @AllowAnonymous
    @NotBlock(level = ProtectLevel.DATA_CHECKING)
    public ResponseEntity<Resource> download(@RequestParam("uid") long uid,
                                             @RequestParam("path") @ValidPath String path) throws IOException {
        String parentPath = PathUtils.getParentPath(path);
        String fileName = PathUtils.getLastNode(path);
        Resource resource = diskFileSystemManager.getMainFileSystem().getResource(uid, parentPath, fileName);
        if (resource == null) {
            throw new SfcJsonException(FileSystemError.FILE_NOT_FOUND);
        }
        return ResourceUtils.wrapResource(resource, fileName);
    }

    /** 上传文件 */
    @PostMapping("upload")
    @AllowAnonymous
    public SfcJsonResult<Long> upload(@RequestParam("uid") long uid,
                                   @RequestParam("path") @ValidPath String path,
                                   @RequestParam(value = "name", required = false) @FileName String name,
                                   @RequestParam(value = "mtime", required = false) Long mtime,
                                   @RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new SfcJsonException(400, "文件为空");
        }
        FileInfo fileInfo = new FileInfo(file);
        fileInfo.setUid(uid);
        if (SfcStringUtils.hasText(name)) {
            fileInfo.setName(name);
        }
        if (mtime != null) {
            fileInfo.setMtime(mtime);
        }
        return SfcJsonResult.getInstance(diskFileSystemManager.getMainFileSystem().saveFile(fileInfo, path));
    }

    /** 重命名文件 */
    @PostMapping("rename")
    @AllowAnonymous
    public SfcJsonResult<Object> rename(@RequestParam("uid") long uid,
                                     @RequestParam("path") @ValidPath String path,
                                     @RequestParam("oldName") @FileName String oldName,
                                     @RequestParam("newName") @FileName String newName) throws IOException {
        diskFileSystemManager.getMainFileSystem().rename(uid, path, oldName, newName);
        return SfcJsonResult.emptySuccess();
    }

    /**
     * 创建目录。
     *
     * <p>目录按「父路径 + 名称」定位，父级目录不存在时连同父级一并创建（等价于 {@code mkdir -p}），
     * 因此调用方无需先确认父目录存在；若目标已存在同名的文件（非目录）则由文件系统抛错。</p>
     *
     * @param uid  资源所属用户ID，0 表示公共网盘，写入公共网盘需要管理员权限
     * @param path 新目录所在的父路径，根目录为 "/"
     * @param name 新目录名称
     * @return 空成功响应
     * @throws IOException 文件系统访问异常
     */
    @PostMapping("mkdirs")
    @AllowAnonymous
    public SfcJsonResult<Object> mkdirs(@RequestParam("uid") long uid,
                                     @RequestParam("path") @ValidPath String path,
                                     @RequestParam("name") @FileName String name) throws IOException {
        diskFileSystemManager.getMainFileSystem().mkdirs(uid, SfcStringUtils.appendPath(path, name));
        return SfcJsonResult.emptySuccess();
    }

    /** 复制文件 */
    @PostMapping("copy")
    @AllowAnonymous
    public SfcJsonResult<Object> copy(@RequestParam("uid") long uid,
                                   @RequestParam(value = "sourceUid", required = false) Long sourceUid,
                                   @RequestParam(value = "targetUid", required = false) Long targetUid,
                                   @RequestParam("sourcePath") @ValidPath String sourcePath,
                                   @RequestParam("targetPath") @ValidPath String targetPath,
                                   @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite,
                                   @RequestBody List<String> names) throws IOException {
        long fromUid = sourceUid == null ? uid : sourceUid;
        long toUid = targetUid == null ? uid : targetUid;
        SfcUIDValidator.validateWithException(fromUid, false);
        SfcUIDValidator.validateWithException(toUid, true);
        requireNames(names);
        SimpleFileTransferParam param = SimpleFileTransferParam.builder()
                .sourceUid(fromUid)
                .sourcePath(sourcePath)
                .files(names)
                .targetUid(toUid)
                .targetPath(targetPath)
                .isOverwrite(overwrite)
                .build();
        diskFileSystemManager.getMainFileSystem().copy(param, null);
        return SfcJsonResult.emptySuccess();
    }

    /** 移动文件 */
    @PostMapping("move")
    @AllowAnonymous
    public SfcJsonResult<Object> move(@RequestParam("uid") long uid,
                                    @RequestParam(value = "sourceUid", required = false) Long sourceUid,
                                    @RequestParam(value = "targetUid", required = false) Long targetUid,
                                    @RequestParam("sourcePath") @ValidPath String sourcePath,
                                    @RequestParam("targetPath") @ValidPath String targetPath,
                                    @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite,
                                    @RequestBody List<String> names) throws IOException {
        long fromUid = sourceUid == null ? uid : sourceUid;
        long toUid = targetUid == null ? uid : targetUid;
        SfcUIDValidator.validateWithException(fromUid, true);
        SfcUIDValidator.validateWithException(toUid, true);
        requireNames(names);
        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        for (String name : names) {
            fileSystem.move(fromUid, sourcePath, toUid, targetPath, name, overwrite);
        }
        return SfcJsonResult.emptySuccess();
    }

    /** 删除文件 */
    @PostMapping("delete")
    @AllowAnonymous
    public SfcJsonResult<Object> delete(@RequestParam("uid")  long uid,
                                     @RequestParam("path") @ValidPath String path,
                                     @RequestBody List<String> names) throws IOException {
        requireNames(names);
        diskFileSystemManager.getMainFileSystem().deleteFile(uid, path, names);
        return SfcJsonResult.emptySuccess();
    }

    /** 校验文件名列表非空 */
    private void requireNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new SfcJsonException(400, "文件名列表不能为空");
        }
    }
}
