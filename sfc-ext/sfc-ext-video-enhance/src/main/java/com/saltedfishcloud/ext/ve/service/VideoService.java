package com.saltedfishcloud.ext.ve.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.dao.EncodeConvertTaskRepo;
import com.saltedfishcloud.ext.ve.model.EncodeConvertRule;
import com.saltedfishcloud.ext.ve.model.EncodeConvertTaskParam;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.model.VideoInfo;
import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTask;
import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTaskLog;
import com.saltedfishcloud.ext.ve.utils.VideoResourceUtils;
import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedProtocolException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.*;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.saltedfishcloud.ext.ve.constant.VEConstants.*;

@Service
@Slf4j
@Validated
public class VideoService {
    @Autowired
    private FFMpegHelper ffMpegHelper;

    @Autowired
    private EncodeConvertTaskRepo encodeConvertTaskRepo;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Autowired
    private AsyncTaskManager asyncTaskManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 检查配置是否正确
     */
    public void check() {
        VEProperty property = ffMpegHelper.getProperty();
        if (!StringUtils.hasText(property.getFfmpegPath())) {
            throw new JsonException("未配置ffmpeg路径");
        }
        try {
            ffMpegHelper.getFFMpegInfo();
        } catch (IOException e) {
            throw new JsonException("检查失败，错误：" + e.getMessage());
        }
    }

    /**
     * 记录观看进度
     * @param uid       观看用户id
     * @param identify  视频标识，可通过相同的标识获取进度
     * @param time      观看进度
     */
    public void recordWatchProgress(@UID(value = true) Long uid, String identify, double time) {
        redisTemplate.opsForValue().set("watch_progress::" + uid + "::" + identify, time, Duration.ofDays(7));
    }

    /**
     * 获取观看进度
     * @param uid       观看用户id
     * @param identify  视频标识
     * @return          观看进度，返回null就是没有记录
     */
    public Double getWatchProgress(@UID(value = true) Long uid, String identify) {
        return (Double) redisTemplate.opsForValue().get("watch_progress::" + uid + "::" + identify);
    }

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
            if (Objects.equals(AsyncTaskConstants.Status.RUNNING, e.getAsyncTaskRecord().getStatus())) {
                try {
                    ProgressRecord progress = asyncTaskManager.getProgress(e.getAsyncTaskRecord().getId());
                    if (progress != null) {
                        e.setProgress(progress);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        return CommonPageInfo.of(tasks);
    }

    /**
     * 获取请求的视频资源的实际资源来源。
     * @param param 若额外参数中设置了sourceProtocol，则表示指定要提取的视频资源的请求协议，设置sourceId指定要提取的目标资源id。
     * @return  如果资源请求未指定实际的资源来源，则返回本次请求参数本身
     */
    public ResourceRequest getSourceResourceRequest(ResourceRequest param) {
        String sourceProtocol = TypeUtils.toString(param.getParams().get("sourceProtocol"));

        // 若指定了文件来源协议，则从来源协议处理器中获取资源（如：从文件分享中获取，或是其他拓展的协议）
        if (sourceProtocol != null) {
            if (sourceProtocol.equals(param.getProtocol())) {
                throw new IllegalArgumentException("来源协议不能与请求协议相同");
            }
            String sourceId = TypeUtils.toString(param.getParams().get("sourceId"));
            if (sourceId == null) {
                throw new IllegalArgumentException("缺少sourceId");
            }
            ResourceRequest nextRequest = new ResourceRequest();
            BeanUtils.copyProperties(param, nextRequest);
            nextRequest.setParams(new HashMap<>(param.getParams()));
            nextRequest.setProtocol(sourceProtocol);
            nextRequest.setTargetId(sourceId);
            return nextRequest;
        } else {
            return param;
        }
    }

    /**
     * 提取资源拓展方法，支持通过sourceProtocol和sourceId获取资源的依赖资源
     */
    public Resource getResource(ResourceRequest param) throws IOException {
        ResourceRequest realResourceRequest = getSourceResourceRequest(param);

        // 若指定了文件来源协议，则从来源协议处理器中获取资源（如：从文件分享中获取，或是其他拓展的协议）
        if (realResourceRequest != param) {
            try {
                return resourceService.getResource(realResourceRequest);
            } catch (UnsupportedProtocolException e) {
                throw new RuntimeException(e);
            }
        } else {
            return diskFileSystemManager.getMainFileSystem().getResource(Long.parseLong(param.getTargetId()), param.getPath(), param.getName());
        }
    }

    /**
     * 获取字幕信息列表
     * @param resource  视频文件资源
     */
    public VideoInfo getVideoInfo(Resource resource) throws IOException {
        String localPath = VideoResourceUtils.toLocalPath(resource);
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
        String localPath = VideoResourceUtils.toLocalPath(resource);
        return ffMpegHelper.extractSubtitle(localPath, streamNo, type);
    }

    /**
     * 创建视频编码转换任务
     * @param param  任务参数
     * @return      任务ID
     */
    public String createEncodeConvertTask(EncodeConvertTaskParam param) throws IOException {
        Long uid = SecureUtils.getSpringSecurityUser().getId();
        EncodeConvertTask taskPo = createTaskPo(param);
        boolean isHandleVideo = param.getRules().stream().anyMatch(e -> ConvertTaskType.VIDEO.equals(e.getType()) && EncodeMethod.CONVERT.equals(e.getMethod()));
        AsyncTaskRecord record = AsyncTaskRecord.builder()
                .name("视频编码转换")
                .taskType(VEConstants.TASK_TYPE)
                .params(MapperHolder.toJson(param))
                .status(AsyncTaskConstants.Status.WAITING)
                // 涉及到视频重编码时，开销设定跑16个CPU核心以便让多核CPU平台上能同时运行多个视频转换，非多核平台上最多只能运行一个视频编码转换任务，且运行期间不再接收其他任务
                .cpuOverhead(isHandleVideo ? 1600 : 100)
                .build();
        record.setUid(uid);


        taskPo.setAsyncTaskRecord(record);
        taskPo.setUid(uid);
        asyncTaskManager.submitAsyncTask(record);
        encodeConvertTaskRepo.save(taskPo);
        return record.getId() + "";
    }

    /**
     * 获取编码转换任务的日志
     * @param taskId    编码转换任务的异步任务id
     */
    public EncodeConvertTaskLog getTaskLog(Long taskId) throws IOException {
        Resource taskLog = asyncTaskManager.getTaskLog(taskId, true);
        String logStr = ResourceUtils.resourceToString(taskLog);
        return EncodeConvertTaskLog.builder()
                .taskId(taskId)
                .taskLog(logStr)
                .build();
    }

    private EncodeConvertTask createTaskPo(EncodeConvertTaskParam param) throws JsonProcessingException {
        EncodeConvertTask task = new EncodeConvertTask();
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
