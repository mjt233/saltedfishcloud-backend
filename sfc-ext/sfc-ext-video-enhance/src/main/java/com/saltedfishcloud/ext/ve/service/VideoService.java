package com.saltedfishcloud.ext.ve.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.saltedfishcloud.ext.ve.constant.VEConstants.*;

@Service
@Slf4j
public class VideoService {
    private final static String LOG_PREFIX = "[视频增强服务]";
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

    /**
     * 获取视频编码转换任务列表
     * @param uid       用户id
     * @param status    任务状态
     * @param page      页码，首页为0
     * @param pageSize  页大小
     */
    public CommonPageInfo<EncodeConvertTask> listTask(Long uid, Integer status, Integer page, Integer pageSize) {
        PageRequest pageRequest = PageRequest.of(page, pageSize);
        Page<EncodeConvertTask> tasks = encodeConvertTaskRepo.findByUidAndTaskStatus(uid, status, pageRequest);

        // 针对运行中的任务获取相关进度
        tasks.forEach(e -> {
            if (Objects.equals(TaskStatus.RUNNING, e.getTaskStatus())) {
                TaskContext<EncodeConvertAsyncTask> taskContext = asyncTaskManager.getContext(e.getTaskId(), EncodeConvertAsyncTask.class);
                if (taskContext != null) {
                    e.setProgress(taskContext.getTask().getProgressRecord());
                }
            }
        });
        return CommonPageInfo.of(tasks);
    }

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
    public String createEncodeConvertTask(EncodeConvertTaskParam param) throws IOException {
        Integer uid = SecureUtils.getSpringSecurityUser().getId();
        param.getTarget().getParams().put("createUId", uid + "");
        EncodeConvertAsyncTask task = encodeConvertAsyncTaskFactory.createTask(param);
        TaskContext<EncodeConvertAsyncTask> context = taskContextFactory.createContextFromAsyncTask(task);

        Path tempDir = Paths.get(StringUtils.appendSystemPath(PathUtils.getTempDirectory(), context.getId()));
        Files.createDirectories(tempDir);
        task.setOutputFile(StringUtils.appendSystemPath(PathUtils.getTempDirectory(), context.getId(), param.getTarget().getName()));
        log.info("{}创建临时目录：{}", LOG_PREFIX, tempDir);

        String taskId = context.getId();
        EncodeConvertTask taskPo = createTaskPo(param);
        taskPo.setUid(Long.valueOf(uid));
        context.onStart(() -> {
            taskPo.setTaskId(taskId);
            taskPo.setTaskStatus(TaskStatus.RUNNING);
            encodeConvertTaskRepo.save(taskPo);
        });
        context.onSuccess(() -> {
            taskPo.setTaskStatus(TaskStatus.SUCCESS);
            encodeConvertTaskRepo.save(taskPo);
        });
        context.onFailed(() -> {
            taskPo.setTaskStatus(TaskStatus.FAILED);
            encodeConvertTaskRepo.save(taskPo);
        });
        context.onFinish(() -> {
            try {
                Files.deleteIfExists(tempDir);
                log.info("{}移除临时目录：{}", LOG_PREFIX, tempDir);
            } catch (IOException e) {
                log.error("{}临时目录移除失败：", LOG_PREFIX, e);
            }
        });
        asyncTaskManager.submit(context);
        return taskId;
    }

    private EncodeConvertTask createTaskPo(EncodeConvertTaskParam param) throws JsonProcessingException {
        EncodeConvertTask task = new EncodeConvertTask();
        task.setTaskStatus(TaskStatus.WAITING);
        task.setParams(MapperHolder.toJson(param));

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
