package com.xiaotao.saltedfishcloud.download.service;

import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.constant.MQTopic;
import com.xiaotao.saltedfishcloud.download.AsyncDownloadTask;
import com.xiaotao.saltedfishcloud.download.DownloadService;
import com.xiaotao.saltedfishcloud.download.repo.DownloadTaskRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.download.model.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.model.param.TaskType;
import com.xiaotao.saltedfishcloud.download.model.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;

/**
 * 离线下载服务实现类
 * @TODO 代码太烂太乱，需要重构重新实现这个模块
 */
@Slf4j
@Service
public class DownloadServiceImpl implements DownloadService, InitializingBean {
    static final private Collection<Integer> FINISH_TYPE = Arrays.asList(
            AsyncTaskConstants.Status.FAILED,
            AsyncTaskConstants.Status.FINISH,
            AsyncTaskConstants.Status.CANCEL
    );
    static final private Collection<Integer> DOWNLOADING_TYPE = Arrays.asList(
            AsyncTaskConstants.Status.RUNNING,
            AsyncTaskConstants.Status.WAITING
    );

    public static final String LOG_TITLE = "[Download]";

    @Resource
    private DownloadTaskRepo downloadDao;

    @Resource
    private DiskFileSystemManager fileSystemProvider;

    @Resource
    private ProgressDetector detector;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RedisMessageListenerContainer listenerContainer;

    @Autowired
    private TaskManager taskManager;

    @Autowired
    private AsyncTaskManager asyncTaskManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        subscribeInterrupt();
    }

    /**
     * 订阅中断通知，收到来自消息队列的中断任务ID时尝试在当前任务管理器中
     */
    protected void subscribeInterrupt() {
        listenerContainer.addMessageListener((message, pattern) -> {
            final String interruptId = (String)redisTemplate.getValueSerializer().deserialize(message.getBody());
            final boolean res = tryInterrupt(interruptId);
            log.debug("{}收到中断任务请求：{}，执行结果：{}", LOG_TITLE, interruptId, res);
        }, new PatternTopic(MQTopic.DOWNLOAD_TASK_INTERRUPT));
    }

    /**
     * 尝试在当前任务管理器中中断任务下载
     * @param id    要中断的任务ID
     * @return 是否成功中断。因为在分布式部署中，当前实例进程收到中断通知但下载任务不一定是由该实例执行。
     */
    protected boolean tryInterrupt(String id) {
        final TaskContext<AsyncDownloadTask> context = taskManager.getContext(id, AsyncDownloadTask.class);
        if (context == null) {
            return false;
        }
        context.interrupt();
        return true;
    }

    @Override
    public void interrupt(String id) throws IOException {
        final DownloadTaskInfo info = downloadDao.getOne(id);
        AsyncTaskRecord asyncTaskRecord = info.getAsyncTaskRecord();
        if (asyncTaskRecord == null) {
            throw new IllegalArgumentException("没有关联异步任务id");
        }
        if (!DOWNLOADING_TYPE.contains(asyncTaskRecord.getStatus())) {
            throw new IllegalArgumentException("只能对下载中或等待中的下载任务操作");
        }
        asyncTaskManager.interrupt(asyncTaskRecord.getId());
    }

    @Override
    public Page<DownloadTaskInfo> getTaskList(int uid, int page, int size, TaskType type) {
        Page<DownloadTaskInfo> tasks;
        PageRequest pageRequest = PageRequest.of(page, size);
        if (type == null || type == TaskType.ALL) {
            tasks = downloadDao.findByUid(uid, pageRequest);
        } else if (type == TaskType.DOWNLOADING) {
            tasks = downloadDao.findByUidAndState(uid, DOWNLOADING_TYPE, pageRequest);
        } else {
            tasks = downloadDao.findByUidAndState(uid, FINISH_TYPE, pageRequest);
        }

        tasks.forEach(e -> {
            AsyncTaskRecord asyncTaskRecord = e.getAsyncTaskRecord();
            if (asyncTaskRecord != null && AsyncTaskConstants.Status.RUNNING.equals(asyncTaskRecord.getStatus())) {
                try {
                    ProgressRecord progress = asyncTaskManager.getProgress(asyncTaskRecord.getId());
                    e.setLoaded(progress.getLoaded());
                    e.setSize(progress.getTotal());
                    e.setSpeed(progress.getSpeed());
                } catch (IOException ex) {
                    log.error("{}获取离线下载任务进度失败", LOG_TITLE, ex);
                }
            }
        });
        return tasks;
    }

    @Override
    public String createTask(DownloadTaskParams params, int creator) throws IOException {

        // 初始化下载任务信息和录入数据库
        DownloadTaskInfo info = new DownloadTaskInfo();
        AsyncTaskRecord asyncTaskRecord = AsyncTaskRecord.builder()
                .name("离线下载")
                .taskType(AsyncTaskType.OFFLINE_DOWNLOAD)
                .cpuOverhead(20)
                .build();
        asyncTaskRecord.setId(IdUtil.getId());
        asyncTaskRecord.setUid((long)creator);

        info.setUrl(params.url);
        info.setProxy(params.proxy);
        info.setUid(params.uid);
        info.setCreatedBy(creator);
        info.setSavePath(params.savePath);
        info.setAsyncTaskRecord(asyncTaskRecord);
        downloadDao.save(info);

        params.downloadId = info.getId();
        asyncTaskRecord.setParams(MapperHolder.toJson(params));
        asyncTaskManager.submitAsyncTask(asyncTaskRecord);
        return params.downloadId;
    }
}
