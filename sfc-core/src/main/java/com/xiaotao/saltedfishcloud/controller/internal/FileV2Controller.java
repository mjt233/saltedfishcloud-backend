package com.xiaotao.saltedfishcloud.controller.internal;

import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.SimpleFileTransferParam;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
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
 * 网盘文件操作 V2 控制器。暂仅用于微服务系统内部调用
 *
 * todo 权限需要校验，确保只能服务内调用
 */
@RestController
@RequestMapping(FileV2Controller.PREFIX)
@Validated
@ProtectBlock
@RequiredArgsConstructor
@Tag(name = "网盘文件操作V2")
public class FileV2Controller {

    /** 接口前缀 */
    public static final String PREFIX = "/api/internal/file/v2";

    /** 文件系统管理器 */
    private final DiskFileSystemManager diskFileSystemManager;

    /** 获取文件列表 */
    @GetMapping("list")
    @AllowAnonymous
    @NotBlock
    public JsonResult<List<FileInfo>> list(@RequestParam("uid") @UID long uid,
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
        return JsonResultImpl.getInstance(merged);
    }

    /** 下载文件 */
    @GetMapping("download")
    @AllowAnonymous
    @NotBlock(level = ProtectLevel.DATA_CHECKING)
    public ResponseEntity<Resource> download(@RequestParam("uid") @UID long uid,
                                             @RequestParam("path") @ValidPath String path) throws IOException {
        String parentPath = PathUtils.getParentPath(path);
        String fileName = PathUtils.getLastNode(path);
        Resource resource = diskFileSystemManager.getMainFileSystem().getResource(uid, parentPath, fileName);
        if (resource == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        return ResourceUtils.wrapResource(resource, fileName);
    }

    /** 上传文件 */
    @PostMapping("upload")
    public JsonResult<Long> upload(@RequestParam("uid") @UID(true) long uid,
                                   @RequestParam("path") @ValidPath String path,
                                   @RequestParam(value = "name", required = false) @FileName String name,
                                   @RequestParam(value = "mtime", required = false) Long mtime,
                                   @RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new JsonException(400, "文件为空");
        }
        FileInfo fileInfo = new FileInfo(file);
        fileInfo.setUid(uid);
        if (StringUtils.hasText(name)) {
            fileInfo.setName(name);
        }
        if (mtime != null) {
            fileInfo.setMtime(mtime);
        }
        return JsonResultImpl.getInstance(diskFileSystemManager.getMainFileSystem().saveFile(fileInfo, path));
    }

    /** 重命名文件 */
    @PostMapping("rename")
    public JsonResult<Object> rename(@RequestParam("uid") @UID(true) long uid,
                                     @RequestParam("path") @ValidPath String path,
                                     @RequestParam("oldName") @FileName String oldName,
                                     @RequestParam("newName") @FileName String newName) throws IOException {
        diskFileSystemManager.getMainFileSystem().rename(uid, path, oldName, newName);
        return JsonResult.emptySuccess();
    }

    /** 复制文件 */
    @PostMapping("copy")
    public JsonResult<Object> copy(@RequestParam("uid") @UID long uid,
                                   @RequestParam(value = "sourceUid", required = false) Long sourceUid,
                                   @RequestParam(value = "targetUid", required = false) Long targetUid,
                                   @RequestParam("sourcePath") @ValidPath String sourcePath,
                                   @RequestParam("targetPath") @ValidPath String targetPath,
                                   @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite,
                                   @RequestBody List<String> names) throws IOException {
        long fromUid = sourceUid == null ? uid : sourceUid;
        long toUid = targetUid == null ? uid : targetUid;
        UIDValidator.validateWithException(fromUid, false);
        UIDValidator.validateWithException(toUid, true);
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
        return JsonResult.emptySuccess();
    }

    /** 移动文件 */
    @PostMapping("move")
    public JsonResult<Object> move(@RequestParam("uid") @UID long uid,
                                    @RequestParam(value = "sourceUid", required = false) Long sourceUid,
                                    @RequestParam(value = "targetUid", required = false) Long targetUid,
                                    @RequestParam("sourcePath") @ValidPath String sourcePath,
                                    @RequestParam("targetPath") @ValidPath String targetPath,
                                    @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite,
                                    @RequestBody List<String> names) throws IOException {
        long fromUid = sourceUid == null ? uid : sourceUid;
        long toUid = targetUid == null ? uid : targetUid;
        UIDValidator.validateWithException(fromUid, true);
        UIDValidator.validateWithException(toUid, true);
        requireNames(names);
        DiskFileSystem fileSystem = diskFileSystemManager.getMainFileSystem();
        for (String name : names) {
            fileSystem.move(fromUid, sourcePath, toUid, targetPath, name, overwrite);
        }
        return JsonResult.emptySuccess();
    }

    /** 删除文件 */
    @PostMapping("delete")
    public JsonResult<Object> delete(@RequestParam("uid") @UID(true) long uid,
                                     @RequestParam("path") @ValidPath String path,
                                     @RequestBody List<String> names) throws IOException {
        requireNames(names);
        diskFileSystemManager.getMainFileSystem().deleteFile(uid, path, names);
        return JsonResult.emptySuccess();
    }

    /** 校验文件名列表非空 */
    private void requireNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new JsonException(400, "文件名列表不能为空");
        }
    }
}
