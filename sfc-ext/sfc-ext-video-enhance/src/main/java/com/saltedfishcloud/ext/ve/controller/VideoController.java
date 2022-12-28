package com.saltedfishcloud.ext.ve.controller;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.request.VideoRequest;
import com.saltedfishcloud.ext.ve.service.VideoService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
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
