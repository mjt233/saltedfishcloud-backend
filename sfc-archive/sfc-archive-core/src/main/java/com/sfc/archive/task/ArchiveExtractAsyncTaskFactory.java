package com.sfc.archive.task;

import com.sfc.archive.ArchiveManager;
import com.sfc.archive.model.AsyncArchiveExtractParam;
import com.sfc.task.AsyncTask;
import com.sfc.task.AsyncTaskFactory;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.constant.AsyncTaskType;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 文件在线解压异步任务工厂。
 * <p>
 * 负责根据任务参数 JSON 字符串创建 {@link ArchiveExtractAsyncTask} 实例，
 * 并为其注入所需的 Spring Bean 依赖。
 * </p>
 *
 * @see ArchiveExtractAsyncTask
 * @see AsyncTaskType#ARCHIVE_EXTRACTOR
 */
@Slf4j
public class ArchiveExtractAsyncTaskFactory implements AsyncTaskFactory {

    @Autowired
    private ArchiveManager archiveManager;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    /**
     * 根据序列化的任务参数创建解压异步任务实例。
     *
     * @param params          JSON 格式的 {@link AsyncArchiveExtractParam} 字符串
     * @param asyncTaskRecord 对应的异步任务记录
     * @return 注入了依赖的 {@link ArchiveExtractAsyncTask} 实例
     */
    @Override
    public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
        try {
            AsyncArchiveExtractParam extractParam = MapperHolder.parseJson(params, AsyncArchiveExtractParam.class);
            ArchiveExtractAsyncTask task = new ArchiveExtractAsyncTask(params, extractParam);
            task.setArchiveManager(archiveManager);
            task.setResourceService(resourceService);
            task.setDiskFileSystem(diskFileSystemManager.getMainFileSystem());
            log.debug("创建文件解压异步任务成功, 任务ID: {}", asyncTaskRecord.getId());
            return task;
        } catch (IOException e) {
            log.error("解析文件解压任务参数失败", e);
            throw new RuntimeException("解析任务参数失败", e);
        }
    }

    /**
     * 返回该工厂所创建任务的唯一类型标识。
     *
     * @return 任务类型字符串，对应 {@link AsyncTaskType#ARCHIVE_EXTRACTOR}
     */
    @Override
    public String getTaskType() {
        return AsyncTaskType.ARCHIVE_EXTRACTOR;
    }
}

