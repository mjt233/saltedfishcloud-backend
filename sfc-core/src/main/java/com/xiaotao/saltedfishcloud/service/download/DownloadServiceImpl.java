package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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
public class DownloadServiceImpl implements DownloadService {
    static final private Collection<DownloadTaskInfo.State> FINISH_TYPE = Arrays.asList(
            DownloadTaskInfo.State.FINISH,
            DownloadTaskInfo.State.CANCEL,
            DownloadTaskInfo.State.FAILED
    );
    static final private Collection<DownloadTaskInfo.State> DOWNLOADING_TYPE = Collections.singleton(DownloadTaskInfo.State.DOWNLOADING);

    @Resource
    private DownloadTaskRepo downloadDao;

    @Resource
    private ProxyDao proxyDao;

    @Resource
    private TaskContextFactory factory;

    @Resource
    private NodeService nodeService;

    @Resource
    private DiskFileSystemProvider fileService;

    @Resource
    private DownloadTaskBuilderFactory builderFactory;

    @Resource
    private ProgressDetector detector;

    private final TaskManager taskManager;

    /**
     * 构造器按类型注入
     */
    public DownloadServiceImpl(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public TaskContext<AsyncDownloadTask> getTaskContext(String taskId) {
        return taskManager.getContext(taskId, AsyncDownloadTask.class);
    }

    @Override
    public void interrupt(String id) {
        var context = taskManager.getContext(id, AsyncDownloadTaskImpl.class);
        if (context == null) {
            throw new JsonException(404, id + "不存在");
        } else {
            context.interrupt();
            taskManager.remove(context);
        }
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
            if (e.state == DownloadTaskInfo.State.DOWNLOADING ) {
                var context = taskManager.getContext(e.id, AsyncDownloadTaskImpl.class);
                if (context == null) {
                    final ProgressRecord record = detector.getRecord(e.id);
                    if (record == null) {
                        e.state = DownloadTaskInfo.State.FAILED;
                        e.message = "interrupt";
                        e.finishAt = new Date();
                        downloadDao.save(e);
                    } else {
                        e.loaded = record.getLoaded();
                        e.speed = record.getSpeed()*1000;
                    }
                } else {
                    var task = context.getTask();
                    e.loaded = task.getStatus().loaded;
                    e.speed = task.getStatus().speed*1000;
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
            log.debug("使用代理创建下载任务：" + proxy);
        }

        // 校验参数合法性
        nodeService.getPathNodeByPath(params.uid, params.savePath);

        // 初始化下载任务信息和录入数据库
        var info = new DownloadTaskInfo();
        AsyncDownloadTaskImpl task = builder.build();
        TaskContext<AsyncDownloadTask> context = factory.createContextFromAsyncTask(task);
        task.setTaskId(context.getId());
        info.id = context.getId();
        info.url = params.url;
        info.proxy = params.proxy;
        info.uid = params.uid;
        info.state = DownloadTaskInfo.State.DOWNLOADING;
        info.createdBy = creator;
        info.savePath = params.savePath;
        info.createdAt = new Date();
        downloadDao.save(info);

        // 绑定事件回调
        task.onReady(() -> {
            info.size = task.getStatus().total;
            info.name = task.getStatus().name;
            downloadDao.save(info);
            log.debug("Task ON Ready");
        });
        DiskFileSystem fileService = this.fileService.getFileSystem();

        context.onSuccess(() -> {
            info.state = DownloadTaskInfo.State.FINISH;
            // 获取文件信息（包括md5）
            var tempFile = Paths.get(task.getSavePath());
            var fileInfo = FileInfo.getLocal(tempFile.toString());
            try {
                // 创建预期的保存目录以应对下载完成前用户删除目录的情况
                fileService.mkdirs(params.uid, params.savePath);


                // 更改文件名为下载任务的文件名
                if (task.getStatus().name != null) {
                    fileInfo.setName(task.getStatus().name);
                    info.name = task.getStatus().name;
                } else {
                    info.name = fileInfo.getName();
                }

                // 保存文件到网盘目录
                fileService.moveToSaveFile(params.uid, tempFile, params.savePath, fileInfo);
            } catch (FileAlreadyExistsException e) {
                // 处理用户删除了目录且指定目录路径中存在同名文件的情况
                info.savePath = "/download" + System.currentTimeMillis() + info.savePath;
                try {
                    fileService.mkdirs(params.uid, info.savePath);
                    fileService.moveToSaveFile(params.uid, tempFile, params.savePath, fileInfo);
                    info.state = DownloadTaskInfo.State.FINISH;
                } catch (IOException ex) {
                    // 依旧失败那莫得办法咯
                    info.message = e.getMessage();
                    info.state = DownloadTaskInfo.State.FAILED;
                }
            } catch (Exception e) {
                // 文件保存失败
                e.printStackTrace();
                info.message = e.getMessage();
                info.state = DownloadTaskInfo.State.FAILED;
            }
            info.finishAt = new Date();
            info.size = task.getStatus().total;
            info.loaded = info.size;
            downloadDao.save(info);
        });
        context.onFailed(() -> {
            if (task.isInterrupted()) {
                info.state = DownloadTaskInfo.State.CANCEL;
                info.message = "has been interrupted";
            } else {
                info.state = DownloadTaskInfo.State.FAILED;
                info.message = task.getStatus().error;
            }
            info.loaded = task.getStatus().loaded;
            info.size = task.getStatus().total != -1 ? task.getStatus().total : task.getStatus().loaded;
            info.finishAt = new Date();
            downloadDao.save(info);
        });

        // 提交任务执行
        factory.getManager().submit(context);

        return context.getId();
    }
}
