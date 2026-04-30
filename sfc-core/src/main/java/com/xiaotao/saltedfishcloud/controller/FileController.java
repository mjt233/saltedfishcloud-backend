package com.xiaotao.saltedfishcloud.controller;

import com.sfc.archive.model.DiskFileSystemCompressParam;
import com.sfc.archive.service.DiskFileSystemArchiveService;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskCreateParam;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.FileTransferInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.*;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.wrap.WrapService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;

/**
 * 浏览控制器，提供浏览功能
 */
@RestController
@RequestMapping( FileController.PREFIX + "{uid}")
@Validated
@ProtectBlock
@RequiredArgsConstructor
@Api(tags = "网盘文件基本操作")
public class FileController {
    public static final String PREFIX = "/api/diskFile/";

    private final DiskFileSystemManager fileSystemManager;
    private final DiskFileSystemArchiveService archiveService;
    private final WrapService wrapService;
    private final AsyncTaskManager asyncTaskManager;


    /*
        =======================================
        =                Create               =
        =======================================
     */

    /**
     * 创建文件夹
     */
    @PutMapping("dir/**")
    public JsonResult<Object> mkdir(@PathVariable @UID(true) long uid,
                            HttpServletRequest request,
                            @RequestParam("name") @FileName String name) throws JsonException, IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/dir", request);
        DiskFileSystem fileSystem = fileSystemManager.getMainFileSystem();
        fileSystem.mkdirs(uid, requestPath + "/" + name);
        return JsonResult.emptySuccess();
    }

    /**
     * 上传文件到网盘系统中
     * @param uid   目标用户的ID
     * @param file  接收到的文件
     * @param mtime 文件修改日期
     * @param md5   文件MD5
     */
    @PutMapping("file/**")
    @BreakPoint
    public JsonResult<Long> upload(HttpServletRequest request,
                             @PathVariable @UID(true) long uid,
                             @RequestParam(value = "file", required = false) @MergeFile MultipartFile file,
                             @RequestParam(value = "mtime", required = false) Long mtime,
                             @RequestParam(value = "md5", required = false) String md5) throws JsonException, IOException {
        if (file == null) {
            throw new JsonException(400, "文件为空");
        }
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/file", request);
        FileInfo fileInfo = new FileInfo(file);
        fileInfo.setUid(uid);
        fileInfo.setMd5(md5);
        fileInfo.setMtime(mtime);
        long i = fileSystemManager.getMainFileSystem().saveFile(fileInfo, requestPath);
        return JsonResultImpl.getInstance(i);
    }

    /**
     * 创建多文件打包下载
     * @param uid   资源所属用户ID
     * @param files 打包的文件信息
     * @return 打包码
     */
    @PostMapping("wrap")
    @AllowAnonymous
    public JsonResult<Object> createWrap(@PathVariable @UID long uid,
                                 @RequestBody FileTransferInfo files) {
        String wid = wrapService.registerWrap(uid, files);
        return JsonResultImpl.getInstance(wid);
    }

    /**
     * 通过MD5快速保存一份文件而不需要上传
     * @param uid   用户ID
     * @param md5   文件MD5
     * @param name  文件名
     * @param path  文件保存目录路径
     */
    @PostMapping("quickSave")
    public JsonResult<Boolean> quickSave(@UID @PathVariable long uid,
                                @RequestParam("path") String path,
                                @RequestParam("name") String name,
                                @RequestParam("md5") String md5) throws IOException {
        boolean b = fileSystemManager.getMainFileSystem().quickSave(uid, path, name, md5);
        if (b) {
            return JsonResultImpl.getInstance(true);
        } else {
            return JsonResultImpl.getInstance(200, false, FileSystemError.QUICK_SAVE_NOT_HIT.getMessage());
        }
    }

    /*
        =======================================
        =                 Read                =
        =======================================
     */
    @GetMapping({
            "wrap/{wid}",
            "wrap/{wid}/{alias}"
    })
    @AllowAnonymous
    public void wrapDownload(@PathVariable("wid") String wid,
                             @PathVariable(required = false, value = "alias") String alias,
                             HttpServletResponse response) throws IOException {
        wrapService.writeWrapToServlet(wid, alias, response);
    }

    /**
     * 取网盘中某个目录的文件列表
     * @param uid   目标用户资源的ID
     */
    @AllowAnonymous
    @GetMapping("fileList/byPath/**")
    @NotBlock
    public JsonResult<Collection<? extends FileInfo>[]> getFileList(HttpServletRequest request, @PathVariable @UID long uid) throws IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/fileList/byPath", request);
        Collection<? extends FileInfo>[] fileList = fileSystemManager.getMainFileSystem().getUserFileList(uid, requestPath);
        return JsonResultImpl.getInstance(fileList);

    }

    @ApiOperation("获取指定文件的信息")
    @GetMapping("getFileInfo")
    @AllowAnonymous
    public JsonResult<FileInfo> getFileInfo(@PathVariable @UID long uid, @RequestParam("path") String path, @RequestParam("name") String name) throws IOException {
        List<FileInfo>[] fileList = fileSystemManager.getMainFileSystem().getUserFileList(uid, path);
        FileInfo fileInfo = Optional.ofNullable(fileList[1]).orElse(Collections.emptyList()).stream().filter(e -> Objects.equals(e.getName(), name))
                .findAny()
                .orElse(null);
        return JsonResultImpl.getInstance(fileInfo);
    }

    /**
     * 搜索目标用户网盘中的文件或文件夹
     * @param uid 目标UID，非管理员只能搜索公共用户和自己的资源
     * @param page 页码，起始页为1
     */
    @GetMapping("fileList/byName/{name}")
    @AllowAnonymous
    @NotBlock
    public JsonResult<CommonPageInfo<FileInfo>> search(@PathVariable("name") String key,
                             @PathVariable @UID long uid,
                             @RequestParam(value = "page", defaultValue = "1") Integer page) {
        return JsonResultImpl.getInstance(fileSystemManager.getMainFileSystem().search(uid, key, page));
    }

    /**
     * 获取网盘文件内容（文件下载）
     * @deprecated 该接口将启用，文件下载请使用{@link ResourceController#downloadByMD5(String, int, HttpServletRequest)}替代
     */
    @RequestMapping(value = "content/**", method = {RequestMethod.POST, RequestMethod.GET})
    @AllowAnonymous
    @NotBlock(level = ProtectLevel.DATA_CHECKING)
    @Deprecated
    public ResponseEntity<Resource> download(HttpServletRequest request,@PathVariable @UID long uid)
            throws IOException {
        String prefix = PREFIX + uid + "/content";
        String requestPath = URLUtils.getRequestFilePath(prefix, request);
        String dir = PathUtils.getParentPath(requestPath);
        String name = PathUtils.getLastNode(requestPath);
        Resource resource = fileSystemManager.getMainFileSystem().getResource(uid, dir, name);
        if (resource != null) {
            return ResourceUtils.wrapResource(resource, name);
        } else {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
    }

    /*
        =======================================
        =                Update               =
        =======================================
     */

    /**
     * 支持跨用户网盘的文件复制
     */
    @ApiOperation("网盘文件复制（支持跨用户网盘）")
    @PostMapping("copy")
    public JsonResult<Object> copy(@RequestBody @Validated SimpleFileTransferParam param) throws IOException {
        UIDValidator.validateWithException(param.getSourceUid(), false);
        UIDValidator.validateWithException(param.getTargetUid(), true);
        fileSystemManager.getMainFileSystem().copy(param, null);
        return JsonResult.emptySuccess();
    }

    /**
     * 支持跨用户网盘的文件复制
     */
    @ApiOperation("异步执行网盘文件复制（支持跨用户网盘）")
    @PostMapping("asyncCopy")
    public JsonResult<AsyncTaskRecord> asyncCopy(@RequestBody @Validated SimpleFileTransferParam param) throws IOException {
        UIDValidator.validateWithException(param.getSourceUid(), false);
        UIDValidator.validateWithException(param.getTargetUid(), true);
        AsyncTaskRecord record = asyncTaskManager.createTask(AsyncTaskCreateParam.builder()
                .name("复制文件")
                .params(MapperHolder.toJson(param))
                .isTemp(true)
                .immediate(true)
                .cpuOverhead(10)
                .taskType(AsyncTaskType.FILE_COPY)
                .build());
        return JsonResultImpl.getInstance(record);
    }

    /**
     * 同网盘内的文件移动
     * @param uid       源文件所在用户id
     * @param info      复制参数
     */
    @ApiOperation("网盘文件移动")
    @PostMapping("move")
    public JsonResult<Object> move( @PathVariable("uid") @UID(true) long uid,
                            @RequestBody @Validated FileTransferParam info) throws IOException {
        long sourceUid = uid;
        for (FileItemTransferParam item : info.getFiles()) {
            String source = PathUtils.getParentPath(item.getSource());
            String sourceName = PathUtils.getLastNode(item.getSource());
            String target = PathUtils.getParentPath(item.getTarget());
            fileSystemManager.getMainFileSystem().move(sourceUid, source, target, sourceName,true);
        }
        return JsonResult.emptySuccess();
    }

    /**
     * 移动文件或目录到指定目录下
     * @param uid    用户ID
     */
    @PutMapping("/fromPath/**")
    @Deprecated
    public JsonResult<Object> move(HttpServletRequest request,
                           @PathVariable("uid") @UID(true) long uid,
                           @RequestBody @Valid FileCopyOrMoveInfo info)
            throws IOException {
        String source = URLUtils.getRequestFilePath(PREFIX + uid + "/fromPath", request);
        String target = URLDecoder.decode(info.getTarget(), "UTF-8");
        for (NamePair file : info.getFiles()) {
            fileSystemManager.getMainFileSystem().move(uid, source, target, file.getSource(), info.isOverwrite());
        }
        return JsonResult.emptySuccess();
    }

    /**
     * 重命名文件
     */
    @PutMapping("name/**")
    public JsonResult<Object> rename(HttpServletRequest request,
                             @PathVariable @UID(true) long uid,
                             @RequestParam("oldName") @Valid @FileName String oldName,
                             @RequestParam("newName") @Valid @FileName String newName) throws IOException {
        String from = URLUtils.getRequestFilePath(PREFIX + uid + "/name", request);
        if (newName.length() < 1) {
            throw new JsonException(400, "文件名不能为空");
        }
        fileSystemManager.getMainFileSystem().rename(uid, from, oldName, newName);
        return JsonResult.emptySuccess();
    }




    /*
        =======================================
        =                Delete               =
        =======================================
     */
    /**
     * 删除文件或目录
     */
    @DeleteMapping("content/**")
    public JsonResult<Long> delete(HttpServletRequest request,
                             @PathVariable @UID(true) long uid,
                             @RequestBody @Validated FileNameList fileName) throws IOException {
        String path = URLUtils.getRequestFilePath(PREFIX + uid + "/content", request);
        long res = fileSystemManager.getMainFileSystem().deleteFile(uid, path, fileName.getFileName());
        return JsonResultImpl.getInstance(res);
    }
}
