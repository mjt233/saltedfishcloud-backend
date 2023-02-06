package com.xiaotao.saltedfishcloud.download;

import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.constant.MQTopic;
import com.xiaotao.saltedfishcloud.download.repo.DownloadTaskRepo;
import com.xiaotao.saltedfishcloud.download.service.AsyncDownloadTaskImpl;
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
            AsyncTaskConstants.Status.FINISH
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
    public void interrupt(String id) {
        try {
            final DownloadTaskInfo info = downloadDao.getOne(id);

            info.setState(DownloadTaskInfo.State.CANCEL);
            downloadDao.save(info);

            log.debug("{}发送中断任务请求：{}", LOG_TITLE, id);
            redisTemplate.convertAndSend(MQTopic.DOWNLOAD_TASK_INTERRUPT, id);
        } catch (JpaObjectRetrievalFailureException e) {
            throw new JsonException(404, "找不到id为" + id + "的下载任务");
        }
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
            // 对于下载中的任务，先判断是否在当前实例进程中执行异步下载。如果是则直接从异步任务实例中获取进度。
            // 通过任务id获取不到异步任务实例时，有两种情况：1. 分布式部署的情况下，任务在其他实例中下载 2. 负责下载的进程或实例退出了
            // 对于情况1，可以通过ProgressDetector获取进度。若获取不到进度，那就是下载的进程或实例退出了，下载失败。
            if (e.getState() == DownloadTaskInfo.State.DOWNLOADING ) {
                TaskContext<AsyncDownloadTaskImpl> context = taskManager.getContext(e.getId(), AsyncDownloadTaskImpl.class);
                if (context == null) {
                    final ProgressRecord record = detector.getRecord(e.getId());
                    if (record == null) {
                        e.setState(DownloadTaskInfo.State.FAILED);
                        e.setMessage("interrupt");
                        e.setFinishAt(new Date());
                        downloadDao.save(e);
                    } else {
                        e.setLoaded(record.getLoaded());
                        e.setSpeed(record.getSpeed()*1000);
                    }
                } else {
                    AsyncDownloadTaskImpl task = context.getTask();
                    e.setLoaded(task.getStatus().loaded);
                    e.setSpeed(task.getStatus().speed*1000);
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
