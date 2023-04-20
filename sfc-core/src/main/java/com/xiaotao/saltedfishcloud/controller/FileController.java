package com.xiaotao.saltedfishcloud.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.sfc.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.model.FileTransferInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.param.*;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.wrap.WrapService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
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
    private final WrapService wrapService;


    /*
        =======================================
        =                Create               =
        =======================================
     */

    /**
     * 创建文件夹
     */
    @PutMapping("dir/**")
    public JsonResult mkdir(@PathVariable @UID(true) int uid,
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
     * @param md5   文件MD5
     */
    @PutMapping("file/**")
    @BreakPoint
    public JsonResult upload(HttpServletRequest request,
                             @PathVariable @UID(true) int uid,
                             @RequestParam(value = "file", required = false) @MergeFile MultipartFile file,
                             @RequestParam(value = "md5", required = false) String md5) throws JsonException, IOException {
        if (file == null) {
            throw new JsonException(400, "文件为空");
        }
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/file", request);
        long i = fileSystemManager.getMainFileSystem().saveFile(uid, file, requestPath, md5);
        return JsonResultImpl.getInstance(i);
    }

    @PostMapping("extractArchive/**")
    public JsonResult extractArchive(@PathVariable @UID int uid,
                                     @RequestParam("name") String name,
                                     @RequestParam("dest") String dest,
                                     HttpServletRequest request) throws IOException {
        String path = URLUtils.getRequestFilePath(PREFIX + uid + "/extractArchive", request);

        fileSystemManager.getMainFileSystem().extractArchive(uid, path, name, dest);
        return JsonResult.emptySuccess();
    }

    /**
     * 从网盘创建压缩文件
     * @param uid   用户ID
     * @param files 文件传输信息
     */
    @PostMapping("compress")
    public JsonResult compress(@PathVariable @UID int uid,
                               @RequestBody FileTransferInfo files) throws IOException {
        fileSystemManager.getMainFileSystem().compress(uid, files.getSource(), files.getFilenames(), files.getDest(), ArchiveType.ZIP);
        return JsonResult.emptySuccess();
    }

    /**
     * 创建多文件打包下载
     * @param uid   资源所属用户ID
     * @param files 打包的文件信息
     * @return 打包码
     */
    @PostMapping("wrap")
    @AllowAnonymous
    public JsonResult createWrap(@PathVariable @UID int uid,
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
    public JsonResult quickSave(@UID @PathVariable int uid,
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
    public JsonResult getFileList(HttpServletRequest request, @PathVariable @UID int uid) throws IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/fileList/byPath", request);
        Collection<? extends FileInfo>[] fileList = fileSystemManager.getMainFileSystem().getUserFileList(uid, requestPath);
        return JsonResultImpl.getInstance(fileList);

    }

    @ApiOperation("获取指定文件的信息")
    @GetMapping("getFileInfo")
    @AllowAnonymous
    public JsonResult<FileInfo> getFileInfo(@PathVariable @UID int uid, @RequestParam("path") String path, @RequestParam("name") String name) throws IOException {
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
    public JsonResult search(@PathVariable("name") String key,
                             @PathVariable @UID int uid,
                             @RequestParam(value = "page", defaultValue = "1") Integer page) {
        PageHelper.startPage(page, 10);
        List<FileInfo> res = fileSystemManager.getMainFileSystem().search(uid, key);
        PageInfo<FileInfo> pageInfo = new PageInfo<>(res);
        return JsonResultImpl.getInstance(pageInfo);
    }

    /**
     * 获取网盘文件内容（文件下载）
     * @deprecated 该接口将启用，文件下载请使用{@link ResourceController#downloadByMD5(String, int, HttpServletRequest)}替代
     */
    @RequestMapping(value = "content/**", method = {RequestMethod.POST, RequestMethod.GET})
    @AllowAnonymous
    @NotBlock(level = ProtectLevel.DATA_CHECKING)
    @Deprecated
    public ResponseEntity<Resource> download(HttpServletRequest request,@PathVariable @UID int uid)
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
     * @param uid       源文件所在用户id
     * @param info      复制参数
     */
    @ApiOperation("网盘文件复制（支持跨用户网盘）")
    @PostMapping("copy")
    public JsonResult copy( @PathVariable("uid") @UID(true) long uid,
                            @RequestBody @Validated FileTransferParam info) throws IOException {
        int sourceUid = (int)uid;
        int targetUid = info.getTargetUid().intValue();
        for (FileItemTransferParam item : info.getFiles()) {
            String source = PathUtils.getParentPath(item.getSource());
            String sourceName = PathUtils.getLastNode(item.getSource());
            String target = PathUtils.getParentPath(item.getTarget());
            String targetName = PathUtils.getLastNode(item.getTarget());
            fileSystemManager.getMainFileSystem().copy(sourceUid, source, target, targetUid, sourceName, targetName, true);
        }
        return JsonResult.emptySuccess();
    }

    /**
     * 同网盘内的文件移动
     * @param uid       源文件所在用户id
     * @param info      复制参数
     */
    @ApiOperation("网盘文件移动")
    @PostMapping("move")
    public JsonResult move( @PathVariable("uid") @UID(true) long uid,
                            @RequestBody @Validated FileTransferParam info) throws IOException {
        int sourceUid = (int)uid;
        for (FileItemTransferParam item : info.getFiles()) {
            String source = PathUtils.getParentPath(item.getSource());
            String sourceName = PathUtils.getLastNode(item.getSource());
            String target = PathUtils.getParentPath(item.getTarget());
            fileSystemManager.getMainFileSystem().move(sourceUid, source, target, sourceName,true);
        }
        return JsonResult.emptySuccess();
    }

    /**
     * 复制文件或目录
     * 复制文件或目录到指定目录下
     */
    @PostMapping("fromPath/**")
    @Deprecated
    public JsonResult copy( @PathVariable("uid") @UID(true) int uid,
                            @RequestBody @Validated FileCopyOrMoveInfo info,
                            HttpServletRequest request) throws IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/fromPath", request);
        String source = URLDecoder.decode(requestPath, "UTF-8");
        String target = URLDecoder.decode(info.getTarget(), "UTF-8");
        for (NamePair file : info.getFiles()) {
            fileSystemManager.getMainFileSystem().copy(uid, source, target, uid, file.getSource(), file.getTarget(), info.isOverwrite());
        }
        return JsonResult.emptySuccess();
    }

    /**
     * 移动文件或目录到指定目录下
     * @param uid    用户ID
     */
    @PutMapping("/fromPath/**")
    @Deprecated
    public JsonResult move(HttpServletRequest request,
                           @PathVariable("uid") @UID(true) int uid,
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
    public JsonResult rename(HttpServletRequest request,
                             @PathVariable @UID(true) int uid,
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
    public JsonResult delete(HttpServletRequest request,
                             @PathVariable @UID(true) int uid,
                             @RequestBody @Validated FileNameList fileName) throws IOException {
        String path = URLUtils.getRequestFilePath(PREFIX + uid + "/content", request);
        long res = fileSystemManager.getMainFileSystem().deleteFile(uid, path, fileName.getFileName());
        return JsonResultImpl.getInstance(res);
    }
}
