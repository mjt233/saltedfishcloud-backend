package com.xiaotao.saltedfishcloud.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.xiaotao.saltedfishcloud.annotations.ReadOnlyBlock;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.po.param.FileCopyOrMoveInfo;
import com.xiaotao.saltedfishcloud.po.param.FileNameList;
import com.xiaotao.saltedfishcloud.po.param.NamePair;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.http.ResponseService;
import com.xiaotao.saltedfishcloud.utils.URLUtils;
import com.xiaotao.saltedfishcloud.validator.FileName;
import com.xiaotao.saltedfishcloud.validator.UID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.List;

/**
 * 浏览控制器，提供浏览功能
 */
@RestController
@RequestMapping( FileController.PREFIX + "{uid}")
@Validated
@ReadOnlyBlock()
public class FileController {
    public static final String PREFIX = "/api/diskFile/";

    @Resource
    private FileService fileService;
    @Resource
    private ResponseService responseService;


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
                            @RequestParam("name") @FileName String name) throws HasResultException, NoSuchFileException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/dir", request);
        fileService.mkdir(uid, requestPath, name);
        return JsonResult.getInstance();
    }

    /**
     * 上传文件到网盘系统中
     * @param uid   目标用户的ID
     * @param file  接收到的文件
     * @param md5   文件MD5
     */
    @PutMapping("file/**")
    public JsonResult upload(HttpServletRequest request,
                             @PathVariable @UID(true) int uid,
                             @RequestParam("file") MultipartFile file,
                             @RequestParam(value = "md5", required = false) String md5) throws HasResultException, IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/file", request);
        int i = fileService.saveFile(uid, file, requestPath, md5);
        return JsonResult.getInstance(i);
    }

    /*
        =======================================
        =                 Read                =
        =======================================
     */

    /**
     * 取网盘中某个目录的文件列表
     * @param uid   目标用户资源的ID
     */
    @AllowAnonymous
    @GetMapping("fileList/byPath/**")
    @NotBlock
    public JsonResult getFileList(HttpServletRequest request, @PathVariable @UID int uid) throws IOException {
        String requestPath = URLUtils.getRequestFilePath(PREFIX + uid + "/fileList/byPath", request);
        Collection<? extends FileInfo>[] fileList = fileService.getUserFileList(uid, requestPath);
        return JsonResult.getInstance(fileList);

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
        List<FileInfo> res = fileService.search(uid, key);
        PageInfo<FileInfo> pageInfo = new PageInfo<>(res);
        return JsonResult.getInstance(pageInfo);
    }

    /**
     * 获取网盘文件内容（文件下载）
     */
    @RequestMapping(value = "content/**", method = {RequestMethod.POST, RequestMethod.GET})
    @AllowAnonymous
    public ResponseEntity<org.springframework.core.io.Resource> download(HttpServletRequest request,
                                                                         @PathVariable @UID int uid)
            throws MalformedURLException, UnsupportedEncodingException, NoSuchFileException {
        String prefix = PREFIX + uid + "/content";
        String requestPath = URLUtils.getRequestFilePath(prefix, request);

        return responseService.getUserFileResponse(uid, requestPath);
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
            fileService.copy(uid, source, target, uid, file.getSource(), file.getTarget(), info.isOverwrite());
        }
        return JsonResult.getInstance();
    }

    /**
     * 移动文件或目录到指定目录下
     * @param uid    用户ID
     */
    @PutMapping("/fromPath/**")
    public JsonResult move(HttpServletRequest request,
                           @PathVariable("uid") @UID(true) int uid,
                           @RequestBody @Valid FileCopyOrMoveInfo info)
            throws UnsupportedEncodingException {
        String source = URLUtils.getRequestFilePath(PREFIX + uid + "/fromPath", request);
        String target = URLDecoder.decode(info.getTarget(), "UTF-8");
        for (NamePair file : info.getFiles()) {
            fileService.move(uid, source, target, file.getSource(), info.isOverwrite());
        }
        return JsonResult.getInstance();
    }

    /**
     * 重命名文件
     */
    @PutMapping("name/**")
    public JsonResult rename(HttpServletRequest request,
                             @PathVariable @UID(true) int uid,
                             @RequestParam("oldName") @Valid @FileName String oldName,
                             @RequestParam("newName") @Valid @FileName String newName) throws HasResultException, NoSuchFileException {
        String from = URLUtils.getRequestFilePath(PREFIX + uid + "/name", request);
        if (newName.length() < 1) {
            throw new HasResultException(400, "文件名不能为空");
        }
        fileService.rename(uid, from, oldName, newName);
        return JsonResult.getInstance();
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
        long res = fileService.deleteFile(uid, path, fileName.getFileName());
        return JsonResult.getInstance(res);
    }
}
