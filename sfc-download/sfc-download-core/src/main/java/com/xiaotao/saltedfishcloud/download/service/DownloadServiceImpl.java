package com.xiaotao.saltedfishcloud.download.service;

import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.download.DownloadService;
import com.xiaotao.saltedfishcloud.download.model.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.download.model.DownloadTaskParams;
import com.xiaotao.saltedfishcloud.download.repo.DownloadTaskRepo;
import com.xiaotao.saltedfishcloud.model.param.TaskType;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 离线下载服务实现类
 */
@Slf4j
@Service
public class DownloadServiceImpl implements DownloadService {
    static final private Collection<Integer> FINISH_TYPE = Arrays.asList(
            AsyncTaskConstants.Status.FAILED,
            AsyncTaskConstants.Status.FINISH,
            AsyncTaskConstants.Status.CANCEL
    );
    static final private Collection<Integer> DOWNLOADING_TYPE = Arrays.asList(
            AsyncTaskConstants.Status.RUNNING,
            AsyncTaskConstants.Status.WAITING
    );

    @Autowired
    private ProxyInfoService proxyInfoService;

    public static final String LOG_TITLE = "[Download]";

    @Resource
    private DownloadTaskRepo downloadDao;

    @Autowired
    private AsyncTaskManager asyncTaskManager;

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
    public Page<DownloadTaskInfo> getTaskList(long uid, int page, int size, TaskType type) {
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
    public String createTask(DownloadTaskParams params, long creator) throws IOException {

        // 初始化下载任务信息和录入数据库
        DownloadTaskInfo downloadTask = new DownloadTaskInfo();
        AsyncTaskRecord asyncTaskRecord = AsyncTaskRecord.builder()
                .name("离线下载")
                .taskType(AsyncTaskType.OFFLINE_DOWNLOAD)
                .cpuOverhead(20)
                .build();
        asyncTaskRecord.setId(IdUtil.getId());
        asyncTaskRecord.setUid(creator);

        downloadTask.setUrl(params.url);
        downloadTask.setProxy(params.proxy);
        downloadTask.setUid(params.uid);
        downloadTask.setCreatedBy(creator);
        downloadTask.setSavePath(params.savePath);
        downloadTask.setAsyncTaskRecord(asyncTaskRecord);
        downloadDao.save(downloadTask);

        params.downloadId = downloadTask.getId();
        asyncTaskRecord.setParams(MapperHolder.toJson(params));
        asyncTaskManager.submitAsyncTask(asyncTaskRecord);
        return params.downloadId;
    }

    @Override
    public List<ProxyInfo> listAvailableProxy() {
        List<ProxyInfo> res = new ArrayList<>(proxyInfoService.findByUid(User.PUBLIC_USER_ID));
        Long currentUid = SecureUtils.getCurrentUid();
        if (currentUid != null) {
            res.addAll(proxyInfoService.findByUid(currentUid));
        }
        return res;
    }
}
