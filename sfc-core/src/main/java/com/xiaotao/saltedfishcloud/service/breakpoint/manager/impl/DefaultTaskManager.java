package com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.service.breakpoint.PartParser;
import com.xiaotao.saltedfishcloud.service.breakpoint.entity.TaskMetadata;
import com.xiaotao.saltedfishcloud.service.breakpoint.exception.TaskNotFoundException;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.TaskManager;
import com.xiaotao.saltedfishcloud.service.breakpoint.manager.impl.utils.TaskStorePath;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MergeInputStream;
import com.xiaotao.saltedfishcloud.service.breakpoint.merge.MultipleFileMergeInputStreamGenerator;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorage;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageDomainDefinition;
import com.xiaotao.saltedfishcloud.service.file.store.attach.AttachStorageManager;
import com.xiaotao.saltedfishcloud.utils.StreamCopyResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 断点续传任务管理器，管理任务的创建，查询，删除和文件块的存储
 */
@Slf4j
public class DefaultTaskManager implements TaskManager {

    @Autowired
    private CacheService cacheService;

    /**
     * 断点续传任务分片附属存储。
     */
    private AttachStorage breakpointStorage;

    /**
     * 注册断点续传附属存储域。
     *
     * @param attachStorageManager 附属存储管理器
     */
    @Autowired
    public void setAttachStorageManager(AttachStorageManager attachStorageManager) {
        attachStorageManager.registerStorageDomain(AttachStorageDomainDefinition.builder()
                .id("breakpoint")
                .name("断点续传")
                .description("断点续传任务分片缓存")
                .build());
        breakpointStorage = attachStorageManager.getStorage("breakpoint");
    }

    private String getMetaRedisKey(String id) {
        return CacheKeyPrefixes.BREAKPOINT_META + id;
    }

    private String getFinishPartKey(String id) {
        return CacheKeyPrefixes.BREAKPOINT_FINISH + id;
    }

    /**
     * 创建断点续传任务
     * @param info  任务元数据
     * @return      创建成功后的任务ID
     * @throws IOException 任务数据存储目录无法写入
     */
    @Override
    public String createTask(TaskMetadata info) throws IOException {
        String id = UUID.randomUUID().toString();
        info.setTaskId(id);
        String taskDir = TaskStorePath.getRoot(id);
        breakpointStorage.mkdir(taskDir);
        cacheService.set(getMetaRedisKey(id), info, 7, TimeUnit.DAYS);
        return id;
    }

    /**
     * 查询任务信息
     * @param id 任务ID
     * @return 任务信息，若任务不存在则返回Null
     * @throws IOException 目录读取出错
     */
    @Override
    public TaskMetadata queryTask(String id) throws IOException {

        return cacheService.get(getMetaRedisKey(id));
    }

    /**
     * 清理指定的任务数据
     * @param id    任务ID
     * @throws IOException 目录不可写或任务不存在
     */
    @Override
    public void clear(String id) throws IOException {
        if(queryTask(id) == null) {
            throw new TaskNotFoundException(id);
        }
        String taskPath = TaskStorePath.getRoot(id);
        breakpointStorage.delete(taskPath);
        cacheService.delete(getMetaRedisKey(id));
        cacheService.delete(getFinishPartKey(id));
    }

    /**
     * 保存部分的断点续传任务文件片段
     * @param id        任务ID
     * @param part      文件块编号（从1开始）
     * @param stream    文件流
     */
    @Override
    public void save(String id, String part, InputStream stream) throws IOException {
        if (queryTask(id) == null) {
            throw new TaskNotFoundException(id);
        }

        final String redisKey = getFinishPartKey(id);
        int[] parts = PartParser.parse(part);
        TaskMetadata taskInfo = queryTask(id);
        for (int i : parts) {
            long size = taskInfo.getPartSize(i);
            final String partFile = TaskStorePath.getPartFile(id, i);
            breakpointStorage.saveFile(partFile, out -> {
                long l = StreamUtils.copyRange(stream, out, 0, size - 1);
                log.debug("写入断点续传文件块，文件名：{} 编号：{}  大小：{}",taskInfo.getFileName() ,i, l);
                cacheService.sAdd(redisKey, i);
                return new StreamCopyResult(l, null);
            });
        }
        stream.close();
    }

    @Override
    public List<Integer> getFinishPart(String id) throws IOException {
        Set<Integer> finish = cacheService.sMembers(getFinishPartKey(id));
        if (finish == null) {
            return Collections.emptyList();
        }
        return finish.stream().sorted(Comparator.comparingInt(o -> o)).collect(Collectors.toList());
    }

    @Override
    public boolean isFinish(String id) throws IOException {
        try {
            TaskMetadata taskMetadata = queryTask(id);
            return getFinishPart(id).size() == taskMetadata.getChunkCount();
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public MergeInputStream getMergeInputStream(String id) throws IOException {
        if (!this.isFinish(id)) {
            throw new IllegalStateException("断点续传任务未完成,文件块不完整");
        }


        List<Integer> finishPart = getFinishPart(id);
        Resource[] paths = new Resource[finishPart.size()];
        for (Integer integer : finishPart) {
            paths[integer - 1] = breakpointStorage.getFile(TaskStorePath.getPartFile(id, integer))
                    .orElseThrow(() -> new IllegalStateException("断点续传分片不存在: " + TaskStorePath.getPartFile(id, integer)));
        }
        return new MergeInputStream(new MultipleFileMergeInputStreamGenerator(paths));
    }
}
