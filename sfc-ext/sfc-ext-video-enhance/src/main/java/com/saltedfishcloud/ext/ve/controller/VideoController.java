package com.saltedfishcloud.ext.ve.controller;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.request.VideoRequest;
import com.saltedfishcloud.ext.ve.service.VideoService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/video")
@Validated
public class VideoController {
    @Autowired
    private VideoService videoService;

    @Autowired
    private DiskFileSystemManager fileSystemManager;

    @Autowired
    private FFMpegHelper ffMpegHelper;

    @GetMapping("listConvertTask")
    public JsonResult listConvertTask(@RequestParam("uid") @UID Long uid,
                                      @RequestParam("status") Integer status,
                                      @RequestParam(value = "page", defaultValue = "1") Integer page,
                                      @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        return JsonResultImpl.getInstance(videoService.listTask(uid, status, page, pageSize));
    }

    @GetMapping("getLog")
    public JsonResult getLog(@RequestParam("taskId") Long taskId) {
        return JsonResultImpl.getInstance(videoService.getTaskLog(taskId));
    }

    /**
     * 获取ffmpeg信息
     */
    @GetMapping("getFFMpegInfo")
    public JsonResult getFFMpegInfo() throws IOException {
        return JsonResultImpl.getInstance(ffMpegHelper.getFFMpegInfo());
    }

    /**
     * 编码转换
     */
    @PostMapping("encodeConvert")
    public JsonResult encodeConvert(@RequestBody EncodeConvertTaskParam task) throws IOException {
        String taskId = videoService.createEncodeConvertTask(task);
        return JsonResultImpl.getInstance(taskId);
    }

    @AllowAnonymous
    @GetMapping("getVideoInfo")
    public JsonResult listSubtitle(VideoRequest request) throws IOException {
        Resource resource = fileSystemManager.getMainFileSystem().getResource(Math.toIntExact(request.getUid()), request.getPath(), request.getName());
        return JsonResultImpl.getInstance(videoService.getVideoInfo(resource));
    }


    @AllowAnonymous
    @GetMapping("getSubtitle")
    public String getSubtitle(VideoRequest request) throws IOException {
        Resource resource = fileSystemManager.getMainFileSystem().getResource(Math.toIntExact(request.getUid()), request.getPath(), request.getName());
        return videoService.getSubtitleText(resource, request.getStream(), request.getType());
    }
}
