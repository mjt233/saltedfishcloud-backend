package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.dao.jpa.DownloadTaskRepository;
import com.xiaotao.saltedfishcloud.dao.mybatis.ProxyDao;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.po.file.FileInfo;
import com.xiaotao.saltedfishcloud.po.param.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import com.xiaotao.saltedfishcloud.service.file.FileService;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Date;

@Service
@Slf4j
public class DownloadService {
    @Resource
    private DownloadTaskRepository downloadDao;
    @Resource
    private ProxyDao proxyDao;
    @Resource
    private TaskContextFactory factory;
    @Resource
    private NodeService nodeService;
    @Resource
    private FileService fileService;
    private final TaskManager taskManager;

    /**
     * 构造器按类型注入
     */
    public DownloadService(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public TaskContext<DownloadTask> getTaskContext(String taskId) {
        return taskManager.getContext(taskId, DownloadTask.class);
    }

    public void interrupt(String id) {
        var context = taskManager.getContext(id, DownloadTask.class);
        if (context == null) {
            throw new JsonException(404, id + "不存在");
        } else {
            context.interrupt();
        }
    }

    /**
     * 获取用户的所有下载任务
     * @param uid   要查询的用户ID
     */
    public Page<DownloadTaskInfo> getTaskList(int uid, int page, int size) {
        var tasks = downloadDao.findByUidOrderByCreatedAtDesc(uid, PageRequest.of(page, size));
        tasks.forEach(e -> {
            if (e.state == DownloadTaskInfo.State.DOWNLOADING ) {
                var context = taskManager.getContext(e.id, DownloadTask.class);
                if (context == null) {
                    e.state = DownloadTaskInfo.State.FAILED;
                    e.message = "interrupt";
                    downloadDao.save(e);
                } else {
                    var task = context.getTask();
                    e.loaded = task.getStatus().loaded;
                    e.speed = task.getStatus().speed;
                }
            }
        });
        return tasks;
    }

    /**
     * 创建一个下载任务
     * @param params 任务参数
     * @TODO 使用队列限制同时下载的任务数
     * @return 下载任务ID
     */
    public String createTask(DownloadTaskParams params, int creator) throws NoSuchFileException {
        // 初始化下载任务和上下文
        var builder = DownloadTaskBuilder.create(params.url);
        builder.setHeaders(params.headers);
        builder.setMethod(params.method);
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

        DownloadTask task = builder.build();
        TaskContext<DownloadTask> context = factory.createContextFromAsyncTask(task);
        // 初始化下载任务信息和录入数据库
        var info = new DownloadTaskInfo();
        task.bindingInfo = info;
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
            downloadDao.save(info);
        });

        // 提交任务执行
        factory.getManager().submit(context);

        return context.getId();
    }
}
