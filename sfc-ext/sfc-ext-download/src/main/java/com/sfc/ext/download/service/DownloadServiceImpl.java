package com.sfc.ext.download.service;

import com.sfc.ext.download.DownloadService;
import com.sfc.ext.download.model.po.DownloadTaskInfo;
import com.sfc.ext.download.model.DownloadTaskParams;
import com.sfc.ext.download.repo.DownloadTaskRepo;
import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.model.param.TaskType;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
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
import java.util.Optional;

/**
 * 离线下载服务实现类
 */
@Slf4j
@Service
public class DownloadServiceImpl implements DownloadService {
    static final private Collection<Integer> FINISH_TYPE = Arrays.asList(
            AsyncTaskConstants.Status.FINISH
    );
    static final private Collection<Integer> FAILED_TYPE = Arrays.asList(
            AsyncTaskConstants.Status.FAILED,
            AsyncTaskConstants.Status.CANCEL,
            AsyncTaskConstants.Status.OFFLINE
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
        final DownloadTaskInfo info = downloadDao.getReferenceById(id);
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
        } else if (type == TaskType.FAILED) {
            tasks = downloadDao.findByUidAndState(uid, FAILED_TYPE, pageRequest);
        } else {
            tasks = downloadDao.findByUidAndState(uid, FINISH_TYPE, pageRequest);
        }

        tasks.forEach(e -> {
            AsyncTaskRecord asyncTaskRecord = e.getAsyncTaskRecord();
            if (asyncTaskRecord != null && AsyncTaskConstants.Status.RUNNING.equals(asyncTaskRecord.getStatus())) {
                try {
                    Optional.ofNullable(asyncTaskManager.getProgress(asyncTaskRecord.getId()))
                    .ifPresent(progress -> {
                        e.setLoaded(progress.getLoaded());
                        e.setSize(progress.getTotal());
                        e.setSpeed(progress.getSpeed());
                    });
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
        asyncTaskRecord.setUid(creator);

        // 先保存 DownloadTask 表
        downloadTask.setUrl(params.url);
        downloadTask.setProxy(params.proxy);
        downloadTask.setUid(params.uid);
        downloadTask.setCreatedBy(creator);
        downloadTask.setSavePath(params.savePath);
        downloadDao.save(downloadTask);

        // 拿到 DownloadTask 的id后，再提交 AsyncTaskRecord
        params.downloadId = downloadTask.getId();
        asyncTaskRecord.setParams(MapperHolder.toJson(params));
        asyncTaskManager.submitAsyncTask(asyncTaskRecord);

        // 在 DownloadTask 中反向记录 AsyncTaskRecord的id
        downloadTask.setTaskId(asyncTaskRecord.getId());
        downloadDao.save(downloadTask);
        return params.downloadId;
    }

    @Override
    public List<ProxyInfo> listAvailableProxy() {
        List<ProxyInfo> res = new ArrayList<>(proxyInfoService.findByUid(UserConstants.PUBLIC_USER_ID));
        Long currentUid = SecureUtils.getCurrentUid();
        if (currentUid != null) {
            res.addAll(proxyInfoService.findByUid(currentUid));
        }
        if (!UIDValidator.validate(UserConstants.PUBLIC_USER_ID, true)) {
            return res.stream().filter(proxy -> proxy.getUid().equals(UserConstants.PUBLIC_USER_ID) && !Boolean.TRUE.equals(proxy.getIsProtect())).toList();
        }
        return res;
    }
}
