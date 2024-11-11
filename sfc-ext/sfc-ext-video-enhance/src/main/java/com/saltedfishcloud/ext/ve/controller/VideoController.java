package com.saltedfishcloud.ext.ve.controller;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.FFMpegInfo;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTask;
import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTaskLog;
import com.saltedfishcloud.ext.ve.model.request.VideoRequest;
import com.saltedfishcloud.ext.ve.service.VideoService;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import io.swagger.annotations.ApiOperation;
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
    public JsonResult<CommonPageInfo<EncodeConvertTask>> listConvertTask(@RequestParam("uid") @UID Long uid,
                                                                         @RequestParam("status") Integer status,
                                                                         @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                                         @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        return JsonResultImpl.getInstance(videoService.listTask(uid, status, page, pageSize));
    }

    @GetMapping("getLog")
    public JsonResult<EncodeConvertTaskLog> getLog(@RequestParam("taskId") Long taskId) throws IOException {
        return JsonResultImpl.getInstance(videoService.getTaskLog(taskId));
    }

    @GetMapping("check")
    @ApiOperation("检查插件是否配置正确")
    public JsonResult<Object> check() {
        videoService.check();
        return JsonResult.emptySuccess();
    }

    /**
     * 获取ffmpeg信息
     */
    @GetMapping("getFFMpegInfo")
    public JsonResult<FFMpegInfo> getFFMpegInfo() throws IOException {
        return JsonResultImpl.getInstance(ffMpegHelper.getFFMpegInfo());
    }

    /**
     * 编码转换
     */
    @PostMapping("encodeConvert")
    public JsonResult<String> encodeConvert(@RequestBody EncodeConvertTaskParam task) throws IOException {
        String taskId = videoService.createEncodeConvertTask(task);
        return JsonResultImpl.getInstance(taskId);
    }

    @AllowAnonymous
    @GetMapping("getVideoInfo")
    public JsonResult<VideoInfo> listSubtitle(VideoRequest request) throws IOException {
        Resource resource = fileSystemManager.getMainFileSystem().getResource(request.getUid(), request.getPath(), request.getName());
        return JsonResultImpl.getInstance(videoService.getVideoInfo(resource));
    }


    @AllowAnonymous
    @GetMapping("getSubtitle")
    public String getSubtitle(VideoRequest request) throws IOException {
        Resource resource = fileSystemManager.getMainFileSystem().getResource(request.getUid(), request.getPath(), request.getName());
        return videoService.getSubtitleText(resource, request.getStream(), request.getType());
    }

    /**
     * 记录观看进度
     * @param uid       观看用户id
     * @param identify  视频标识，可通过相同的标识获取进度
     * @param time      观看进度
     */
    @PostMapping("recordWatchProgress")
    public JsonResult<?> recordWatchProgress(@RequestParam("uid") @UID(value = true) Long uid,
                                    @RequestParam("identify") String identify,
                                    @RequestParam("time") double time) {
        videoService.recordWatchProgress(uid, identify, time);
        return JsonResult.emptySuccess();
    }

    /**
     * 获取观看进度
     * @param uid       观看用户id
     * @param identify  视频标识
     * @return          观看进度，返回null就是没有记录
     */
    @GetMapping("getWatchProgress")
    public JsonResult<Double> getWatchProgress(@RequestParam("uid") @UID(value = true) Long uid,
                                             @RequestParam("identify") String identify) {
        return JsonResultImpl.getInstance(videoService.getWatchProgress(uid, identify));
    }
}
