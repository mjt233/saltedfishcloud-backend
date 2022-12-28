package com.saltedfishcloud.ext.ve.service;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.core.EncodeConvertAsyncTask;
import com.saltedfishcloud.ext.ve.core.EncodeConvertAsyncTaskFactory;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.dao.EncodeConvertTaskRepo;
import com.saltedfishcloud.ext.ve.model.EncodeConvertRule;
import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTask;
import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.saltedfishcloud.ext.ve.constant.VEConstants.*;

@Service
public class VideoService {
    @Autowired
    private FFMpegHelper ffMpegHelper;

    @Autowired
    private EncodeConvertAsyncTaskFactory encodeConvertAsyncTaskFactory;

    @Autowired
    private EncodeConvertTaskRepo encodeConvertTaskRepo;

    @Autowired
    @Lazy
    private ResourceService resourceService;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private TaskManager asyncTaskManager;

    @Autowired
    private TaskContextFactory taskContextFactory;

    @Autowired
    private ProgressDetector progressDetector;

    /**
     * 提取资源拓展方法，支持通过sourceProtocol和sourceId获取资源的依赖资源
     */
    public Resource getResource(ResourceRequest param) throws IOException {
        String sourceProtocol = param.getParams().get("sourceProtocol");
        Resource resource;

        // 若指定了文件来源协议，则从来源协议处理器中获取资源（如：从文件分享中获取，或是其他拓展的协议）
        if (sourceProtocol != null) {
            if (sourceProtocol.equals(param.getProtocol())) {
                throw new IllegalArgumentException("来源协议不能与请求协议相同");
            }
            String sourceId = param.getParams().get("sourceId");
            if (sourceId == null) {
                throw new IllegalArgumentException("缺少sourceId");
            }
            ResourceRequest nextRequest = new ResourceRequest();
            BeanUtils.copyProperties(param, nextRequest);
            nextRequest.setParams(new HashMap<>(param.getParams()));
            nextRequest.setProtocol(sourceProtocol);
            nextRequest.setTargetId(sourceId);
            try {
                resource = resourceService.getResource(nextRequest);
            } catch (UnsupportedProtocolException e) {
                throw new RuntimeException(e);
            }
        } else {
            resource = diskFileSystemManager.getMainFileSystem().getResource(Integer.parseInt(param.getTargetId()), param.getPath(), param.getName());
        }
        return resource;
    }

    private String resourceToLocalPath(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("资源为null");
        }
        if (!(resource instanceof PathResource)) {
            throw new IllegalArgumentException("目前仅支持PathResource");
        }
        return ((PathResource) resource).getPath();
    }

    /**
     * 获取字幕信息列表
     * @param resource  视频文件资源
     */
    public VideoInfo getVideoInfo(Resource resource) throws IOException {
        String localPath = resourceToLocalPath(resource);
        return ffMpegHelper.getVideoInfo(localPath);
    }

    /**
     * 获取字幕内容
     * @param resource  视频文件资源
     * @param streamNo  字幕流编号
     * @param type      字幕类型
     */
    public String getSubtitleText(Resource resource, String streamNo, String type) throws IOException {
        if (type == null) {
            type = VEConstants.SubtitleType.WEBVTT;
        }
        if (streamNo == null) {
            throw new IllegalArgumentException("流编号不能为空");
        }
        String localPath = resourceToLocalPath(resource);
        return ffMpegHelper.extractSubtitle(localPath, streamNo, type);
    }

    /**
     * 创建视频编码转换任务
     * @param param  任务参数
     * @return      任务ID
     */
    public String createEncodeConvertTask(EncodeConvertTaskParam param) {
        EncodeConvertAsyncTask task = encodeConvertAsyncTaskFactory.createTask(param);
        TaskContext<EncodeConvertAsyncTask> context = taskContextFactory.createContextFromAsyncTask(task);
        String taskId = context.getId();
        context.onStart(() -> {
            EncodeConvertTask taskPo = createTaskPo(param);
            taskPo.setTaskId(taskId);
            taskPo.setTaskStatus(TaskStatus.RUNNING);
            progressDetector.addObserve(task, taskId);
            encodeConvertTaskRepo.save(taskPo);
        });
        context.onFinish(() -> progressDetector.removeObserve(taskId));
        context.onSuccess(() -> encodeConvertTaskRepo.updateStatusByTaskId(taskId, TaskStatus.SUCCESS));
        context.onFailed(() -> encodeConvertTaskRepo.updateStatusByTaskId(taskId, TaskStatus.FAILED));
        asyncTaskManager.submit(context);
        return taskId;
    }

    private EncodeConvertTask createTaskPo(EncodeConvertTaskParam param) {
        EncodeConvertTask task = new EncodeConvertTask();
        task.setId(IdUtil.getId());
        task.setTaskStatus(TaskStatus.WAITING);

        Map<String, List<EncodeConvertRule>> typeMap = param.getRules().stream().collect(Collectors.groupingBy(EncodeConvertRule::getType));
        boolean videoConvert = typeMap.getOrDefault(EncoderType.VIDEO, Collections.emptyList()).stream().anyMatch(e -> EncodeMethod.CONVERT.equals(e.getMethod()));
        boolean audioConvert = typeMap.getOrDefault(EncoderType.AUDIO, Collections.emptyList()).stream().anyMatch(e -> EncodeMethod.CONVERT.equals(e.getMethod()));
        if (videoConvert) {
            task.setType(ConvertTaskType.VIDEO);
        } else if (audioConvert) {
            task.setType(ConvertTaskType.AUDIO);
        } else {
            task.setType(ConvertTaskType.FORMAT);
        }
        return task;
    }
}
