package com.xiaotao.saltedfishcloud.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ReadOnlyBlock;
import com.xiaotao.saltedfishcloud.compress.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.entity.FileTransferInfo;
import com.xiaotao.saltedfishcloud.entity.JsonResult;
import com.xiaotao.saltedfishcloud.entity.JsonResultImpl;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.entity.po.param.FileCopyOrMoveInfo;
import com.xiaotao.saltedfishcloud.entity.po.param.FileNameList;
import com.xiaotao.saltedfishcloud.entity.po.param.NamePair;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.filesystem.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.http.ResponseService;
import com.xiaotao.saltedfishcloud.service.wrap.WrapInfo;
import com.xiaotao.saltedfishcloud.service.wrap.WrapService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.List;

/**
 * 浏览控制器，提供浏览功能
 */
@RestController
@RequestMapping( FileController.PREFIX + "{uid}")
@Validated
@ReadOnlyBlock()
@RequiredArgsConstructor
public class FileController {
    public static final String PREFIX = "/api/diskFile/";

    private final DiskFileSystemFactory fileService;
    private final ResponseService responseService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WrapService wrapService;


    /*
        =======================================
        =                Create               =
        =======================================
     */

    /**
     * 这啥？为什么会出现在文件相关的控制器里？QAQ
     * @TODO 挪个位
     */
    @GetMapping("info")
    public User getUserInfo(@SessionAttribute User loginUser) {
        return loginUser;
    }

    /**
     * 创建文件夹
     */
    @PutMapping("dir/**")
    public JsonResult mkdir(@PathVariable @UID(true) int uid,
                            HttpServletRequest request,
                            @RequestParam("name") @FileName String name) throws JsonException, IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/dir", request);
        DiskFileSystem fileSystem = fileService.getFileSystem();
        fileSystem.mkdirs(uid, requestPath + "/" + name);
        return JsonResultImpl.getInstance();
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
        if (file == null || file.isEmpty()) {
            throw new JsonException(400, "文件为空");
        }
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/file", request);
        int i = fileService.getFileSystem().saveFile(uid, file, requestPath, md5);
        return JsonResultImpl.getInstance(i);
    }

    @PostMapping("extractArchive/**")
    public JsonResult extractArchive(@PathVariable @UID int uid,
                                     @RequestParam("name") String name,
                                     @RequestParam("dest") String dest,
                                     HttpServletRequest request) throws IOException {
        String path = URLUtils.getRequestFilePath(PREFIX + uid + "/extractArchive", request);

        fileService.getFileSystem().extractArchive(uid, path, name, dest);
        return JsonResultImpl.getInstance();
    }

    /**
     * 从网盘创建压缩文件
     * @param uid   用户ID
     * @param files 文件传输信息
     */
    @PostMapping("compress")
    public JsonResult compress(@PathVariable @UID int uid,
                               @RequestBody FileTransferInfo files) throws IOException {
        fileService.getFileSystem().compress(uid, files.getSource(), files.getFilenames(), files.getDest(), ArchiveType.ZIP);
        return JsonResultImpl.getInstance();
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
        WrapInfo wrapInfo = wrapService.getWrapInfo(wid);
        if (wrapInfo == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        if (alias == null) {
            alias = "打包下载" + System.currentTimeMillis() + ".zip";
        }
        FileTransferInfo files = wrapInfo.getFiles();
        response.setHeader(
                ResourceUtils.Header.ContentDisposition,
                ResourceUtils.generateContentDisposition(alias)
        );
        response.setContentType(FileUtils.getContentType("a.ab123c"));
        OutputStream output = response.getOutputStream();
        fileService.getFileSystem().compressAndWriteOut(wrapInfo.getUid(), files.getSource(), files.getFilenames(), ArchiveType.ZIP, output);
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
        Collection<? extends FileInfo>[] fileList = fileService.getFileSystem().getUserFileList(uid, requestPath);
        return JsonResultImpl.getInstance(fileList);

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
        List<FileInfo> res = fileService.getFileSystem().search(uid, key);
        PageInfo<FileInfo> pageInfo = new PageInfo<>(res);
        return JsonResultImpl.getInstance(pageInfo);
    }

    /**
     * 获取网盘文件内容（文件下载）
     */
    @RequestMapping(value = "content/**", method = {RequestMethod.POST, RequestMethod.GET})
    @AllowAnonymous
    @NotBlock(level = ReadOnlyLevel.DATA_CHECKING)
    public ResponseEntity<org.springframework.core.io.Resource> download(HttpServletRequest request,
                                                                         @PathVariable @UID int uid)
            throws UnsupportedEncodingException {
        String prefix = PREFIX + uid + "/content";
        String requestPath = URLUtils.getRequestFilePath(prefix, request);
        org.springframework.core.io.Resource resource = fileService.getFileSystem().getResource(uid, requestPath, "");
        if (resource != null) {
            return responseService.wrapResource(resource);
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
     * 复制文件或目录
     * 复制文件或目录到指定目录下
     */
    @PostMapping("fromPath/**")
    public JsonResult copy( @PathVariable("uid") @UID(true) int uid,
                            @RequestBody @Validated FileCopyOrMoveInfo info,
                            HttpServletRequest request) throws IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/fromPath", request);
        String source = URLDecoder.decode(requestPath, "UTF-8");
        String target = URLDecoder.decode(info.getTarget(), "UTF-8");
        for (NamePair file : info.getFiles()) {
            fileService.getFileSystem().copy(uid, source, target, uid, file.getSource(), file.getTarget(), info.isOverwrite());
        }
        return JsonResultImpl.getInstance();
    }

    /**
     * 移动文件或目录到指定目录下
     * @TODO 允许空参数target
     * @param uid    用户ID
     */
    @PutMapping("/fromPath/**")
    public JsonResult move(HttpServletRequest request,
                           @PathVariable("uid") @UID(true) int uid,
                           @RequestBody @Valid FileCopyOrMoveInfo info)
            throws IOException {
        String source = URLUtils.getRequestFilePath(PREFIX + uid + "/fromPath", request);
        String target = URLDecoder.decode(info.getTarget(), "UTF-8");
        for (NamePair file : info.getFiles()) {
            fileService.getFileSystem().move(uid, source, target, file.getSource(), info.isOverwrite());
        }
        return JsonResultImpl.getInstance();
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
        fileService.getFileSystem().rename(uid, from, oldName, newName);
        return JsonResultImpl.getInstance();
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
        long res = fileService.getFileSystem().deleteFile(uid, path, fileName.getFileName());
        return JsonResultImpl.getInstance(res);
    }
}
