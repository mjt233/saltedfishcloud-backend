package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.constant.MQTopic;
import com.xiaotao.saltedfishcloud.dao.jpa.DownloadTaskRepo;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.entity.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.entity.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.entity.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.entity.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.entity.po.param.TaskType;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

/**
 * 离线下载服务实现类
 * @TODO 代码太烂太乱，需要重构重新实现这个模块
 */
@Slf4j
@Service
public class DownloadServiceImpl implements DownloadService, InitializingBean {
    static final private Collection<DownloadTaskInfo.State> FINISH_TYPE = Arrays.asList(
            DownloadTaskInfo.State.FINISH,
            DownloadTaskInfo.State.CANCEL,
            DownloadTaskInfo.State.FAILED
    );
    static final private Collection<DownloadTaskInfo.State> DOWNLOADING_TYPE = Collections.singleton(DownloadTaskInfo.State.DOWNLOADING);

    public static final String LOG_TITLE = "[Download]";

    @Resource
    private DownloadTaskRepo downloadDao;

    @Resource
    private ProxyDao proxyDao;

    @Resource
    private TaskContextFactory taskContextFactory;

    @Resource
    private NodeService nodeService;

    @Resource
    private DiskFileSystemProvider fileSystemProvider;

    @Resource
    private DownloadTaskBuilderFactory builderFactory;

    @Resource
    private ProgressDetector detector;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RedisMessageListenerContainer listenerContainer;

    @Autowired
    private TaskManager taskManager;

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
        final DownloadTaskInfo info = downloadDao.getOne(id);
        info.setState(DownloadTaskInfo.State.CANCEL);
        downloadDao.save(info);

        log.debug("{}发送中断任务请求：{}", LOG_TITLE, id);
        redisTemplate.convertAndSend(MQTopic.DOWNLOAD_TASK_INTERRUPT, id);
    }

    @Override
    public Page<DownloadTaskInfo> getTaskList(int uid, int page, int size, TaskType type) {
        Page<DownloadTaskInfo> tasks;
        var pageRequest = PageRequest.of(page, size);
        if (type == null || type == TaskType.ALL) {
            tasks = downloadDao.findByUidOrderByCreatedAtDesc(uid, pageRequest);
        } else if (type == TaskType.DOWNLOADING) {
            tasks = downloadDao.findByUidAndStateInOrderByCreatedAtDesc(uid, DOWNLOADING_TYPE, pageRequest);
        } else {
            tasks = downloadDao.findByUidAndStateInOrderByCreatedAtDesc(uid, FINISH_TYPE, pageRequest);
        }


        tasks.forEach(e -> {
            // 对于下载中的任务，先判断是否在当前实例进程中执行异步下载。如果是则直接从异步任务实例中获取进度。
            // 通过任务id获取不到异步任务实例时，有两种情况：1. 分布式部署的情况下，任务在其他实例中下载 2. 负责下载的进程或实例退出了
            // 对于情况1，可以通过ProgressDetector获取进度。若获取不到进度，那就是下载的进程或实例退出了，下载失败。
            if (e.getState() == DownloadTaskInfo.State.DOWNLOADING ) {
                var context = taskManager.getContext(e.getId(), AsyncDownloadTaskImpl.class);
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
                    var task = context.getTask();
                    e.setLoaded(task.getStatus().loaded);
                    e.setSpeed(task.getStatus().speed*1000);
                }
            }
        });
        return tasks;
    }

    @Override
    public String createTask(DownloadTaskParams params, int creator) throws NoSuchFileException {
        // 初始化下载任务和上下文
        var builder = builderFactory.getBuilder()
                .setUrl(params.url)
                .setHeaders(params.headers)
                .setMethod(params.method);
        if (params.proxy != null && params.proxy.length() != 0) {
            ProxyInfo proxy = proxyDao.getProxyByName(params.proxy);
            if (proxy == null) {
                throw new JsonException(400, "无效的代理：" + params.proxy);
            }
            builder.setProxy(proxy.toProxy());
            log.debug("{}使用代理创建下载任务：{}", LOG_TITLE, proxy);
        }

        // 校验参数合法性
        nodeService.getPathNodeByPath(params.uid, params.savePath);

        // 初始化下载任务信息和录入数据库
        var info = new DownloadTaskInfo();
        AsyncDownloadTaskImpl task = builder.build();
        TaskContext<AsyncDownloadTask> context = taskContextFactory.createContextFromAsyncTask(task);
        task.setTaskId(context.getId());
        info.setId(context.getId());
        info.setUrl(params.url);
        info.setProxy(params.proxy);
        info.setUid(params.uid);
        info.setState(DownloadTaskInfo.State.DOWNLOADING);
        info.setCreatedBy(creator);
        info.setSavePath(params.savePath);
        info.setCreatedAt(new Date());
        downloadDao.save(info);

        // 绑定事件回调
        task.onReady(() -> {
            info.setSize(task.getStatus().total);
            info.setName(task.getStatus().name);
            downloadDao.save(info);
            log.debug("{}任务已就绪,文件名:{} 大小：{}", LOG_TITLE, info.getName(), info.getSize());
        });
        DiskFileSystem fileService = this.fileSystemProvider.getFileSystem();

        context.onSuccess(() -> {
            info.setState(DownloadTaskInfo.State.FINISH);
            // 获取文件信息（包括md5）
            var tempFile = Paths.get(task.getSavePath());
            var fileInfo = FileInfo.getLocal(tempFile.toString());
            try {
                // 创建预期的保存目录以应对下载完成前用户删除目录的情况
                fileService.mkdirs(params.uid, params.savePath);


                // 更改文件名为下载任务的文件名
                if (task.getStatus().name != null) {
                    fileInfo.setName(task.getStatus().name);
                    info.setName(task.getStatus().name);
                } else {
                    info.setName(fileInfo.getName());
                }

                // 保存文件到网盘目录
                fileService.moveToSaveFile(params.uid, tempFile, params.savePath, fileInfo);
            } catch (FileAlreadyExistsException e) {
                // 处理用户删除了目录且指定目录路径中存在同名文件的情况
                info.setSavePath("/download" + System.currentTimeMillis() + info.getSavePath());
                try {
                    fileService.mkdirs(params.uid, info.getSavePath());
                    fileService.moveToSaveFile(params.uid, tempFile, params.savePath, fileInfo);
                    info.setState(DownloadTaskInfo.State.FINISH);
                } catch (IOException ex) {
                    // 依旧失败那莫得办法咯
                    info.setMessage(e.getMessage());
                    info.setState(DownloadTaskInfo.State.FAILED);
                }
            } catch (Exception e) {
                // 文件保存失败
                e.printStackTrace();
                info.setMessage(e.getMessage());
                info.setState(DownloadTaskInfo.State.FAILED);
            }
            info.setFinishAt(new Date());
            info.setSize(task.getStatus().total);
            info.setLoaded(info.getSize());
            downloadDao.save(info);
        });
        context.onFailed(() -> {
            if (task.isInterrupted()) {
                info.setState(DownloadTaskInfo.State.CANCEL);
                info.setMessage("has been interrupted");
            } else {
                info.setState(DownloadTaskInfo.State.FAILED);
                info.setMessage(task.getStatus().error);
            }
            info.setLoaded(task.getStatus().loaded);
            info.setSize(task.getStatus().total != -1 ? task.getStatus().total : task.getStatus().loaded);
            info.setFinishAt(new Date());
            downloadDao.save(info);
        });

        // 提交任务执行
        taskManager.submit(context);

        return context.getId();
    }
}
