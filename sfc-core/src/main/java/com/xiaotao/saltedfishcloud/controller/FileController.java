package com.xiaotao.saltedfishcloud.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.entity.FileTransferInfo;
import com.xiaotao.saltedfishcloud.entity.json.JsonResult;
import com.xiaotao.saltedfishcloud.entity.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.entity.po.param.FileCopyOrMoveInfo;
import com.xiaotao.saltedfishcloud.entity.po.param.FileNameList;
import com.xiaotao.saltedfishcloud.entity.po.param.NamePair;
import com.xiaotao.saltedfishcloud.enums.ArchiveType;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.BreakPoint;
import com.xiaotao.saltedfishcloud.service.breakpoint.annotation.MergeFile;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import com.xiaotao.saltedfishcloud.service.wrap.WrapInfo;
import com.xiaotao.saltedfishcloud.service.wrap.WrapService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.FileName;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
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
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.List;

/**
 * ????????????????????????????????????
 */
@RestController
@RequestMapping( FileController.PREFIX + "{uid}")
@Validated
@ProtectBlock
@RequiredArgsConstructor
public class FileController {
    public static final String PREFIX = "/api/diskFile/";

    private final DiskFileSystemProvider fileService;
    private final WrapService wrapService;


    /*
        =======================================
        =                Create               =
        =======================================
     */

    /**
     * ???????????????
     */
    @PutMapping("dir/**")
    public JsonResult mkdir(@PathVariable @UID(true) int uid,
                            HttpServletRequest request,
                            @RequestParam("name") @FileName String name) throws JsonException, IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/dir", request);
        DiskFileSystem fileSystem = fileService.getFileSystem();
        fileSystem.mkdirs(uid, requestPath + "/" + name);
        return JsonResult.emptySuccess();
    }

    /**
     * ??????????????????????????????
     * @param uid   ???????????????ID
     * @param file  ??????????????????
     * @param md5   ??????MD5
     */
    @PutMapping("file/**")
    @BreakPoint
    public JsonResult upload(HttpServletRequest request,
                             @PathVariable @UID(true) int uid,
                             @RequestParam(value = "file", required = false) @MergeFile MultipartFile file,
                             @RequestParam(value = "md5", required = false) String md5) throws JsonException, IOException {
        if (file == null || file.isEmpty()) {
            throw new JsonException(400, "????????????");
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
        return JsonResult.emptySuccess();
    }

    /**
     * ???????????????????????????
     * @param uid   ??????ID
     * @param files ??????????????????
     */
    @PostMapping("compress")
    public JsonResult compress(@PathVariable @UID int uid,
                               @RequestBody FileTransferInfo files) throws IOException {
        fileService.getFileSystem().compress(uid, files.getSource(), files.getFilenames(), files.getDest(), ArchiveType.ZIP);
        return JsonResult.emptySuccess();
    }

    /**
     * ???????????????????????????
     * @param uid   ??????????????????ID
     * @param files ?????????????????????
     * @return ?????????
     */
    @PostMapping("wrap")
    @AllowAnonymous
    public JsonResult createWrap(@PathVariable @UID int uid,
                                 @RequestBody FileTransferInfo files) {
        String wid = wrapService.registerWrap(uid, files);
        return JsonResultImpl.getInstance(wid);
    }

    /**
     * ??????MD5??????????????????????????????????????????
     * @param uid   ??????ID
     * @param md5   ??????MD5
     * @param name  ?????????
     * @param path  ????????????????????????
     */
    @PostMapping("quickSave")
    public JsonResult quickSave(@UID @PathVariable int uid,
                                @RequestParam("path") String path,
                                @RequestParam("name") String name,
                                @RequestParam("md5") String md5) throws IOException {
        boolean b = fileService.getFileSystem().quickSave(uid, path, name, md5);
        if (b) {
            return JsonResultImpl.getInstance(true);
        } else {
            return JsonResultImpl.getInstance(100, false, FileSystemError.QUICK_SAVE_NOT_HIT.getMessage());
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
        WrapInfo wrapInfo = wrapService.getWrapInfo(wid);
        if (wrapInfo == null) {
            throw new JsonException(FileSystemError.FILE_NOT_FOUND);
        }
        if (alias == null) {
            alias = "????????????" + System.currentTimeMillis() + ".zip";
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
     * ???????????????????????????????????????
     * @param uid   ?????????????????????ID
     */
    @AllowAnonymous
    @GetMapping("fileList/byPath/**")
    @NotBlock
    public JsonResult getFileList(HttpServletRequest request, @PathVariable @UID int uid) throws IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/fileList/byPath", request);
        Collection<? extends FileInfo>[] fileList = fileService.getFileSystem().getUserFileList(uid, requestPath);
        return JsonResultImpl.getInstance(fileList);

    }

    @AllowAnonymous
    @GetMapping("getFileList")
    public JsonResult getFileList(@PathVariable String uid, @RequestParam("path") String path) {
        return JsonResult.emptySuccess();
    }

    /**
     * ????????????????????????????????????????????????
     * @param uid ??????UID?????????????????????????????????????????????????????????
     * @param page ?????????????????????1
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
     * ??????????????????????????????????????????
     * @deprecated ??????????????????????????????????????????{@link ResourceController#downloadByMD5(String, int, HttpServletRequest)}??????
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
        Resource resource = fileService.getFileSystem().getResource(uid, dir, name);
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
     * ?????????????????????
     * ???????????????????????????????????????
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
        return JsonResult.emptySuccess();
    }

    /**
     * ???????????????????????????????????????
     * @TODO ???????????????target
     * @param uid    ??????ID
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
        return JsonResult.emptySuccess();
    }

    /**
     * ???????????????
     */
    @PutMapping("name/**")
    public JsonResult rename(HttpServletRequest request,
                             @PathVariable @UID(true) int uid,
                             @RequestParam("oldName") @Valid @FileName String oldName,
                             @RequestParam("newName") @Valid @FileName String newName) throws IOException {
        String from = URLUtils.getRequestFilePath(PREFIX + uid + "/name", request);
        if (newName.length() < 1) {
            throw new JsonException(400, "?????????????????????");
        }
        fileService.getFileSystem().rename(uid, from, oldName, newName);
        return JsonResult.emptySuccess();
    }




    /*
        =======================================
        =                Delete               =
        =======================================
     */

    /**
     * ?????????????????????
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
