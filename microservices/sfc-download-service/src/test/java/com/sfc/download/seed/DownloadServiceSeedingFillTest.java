package com.sfc.download.seed;

import com.sfc.cloud.remote.client.SfcCoreFileClient;
import com.sfc.cloud.remote.client.SfcCoreProxyClient;
import com.sfc.cloud.task.worker.SfcTaskWorkerClient;
import com.sfc.download.config.SfcDownloadConfigService;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadEngineRegistry;
import com.sfc.download.model.param.TaskType;
import com.sfc.download.model.po.DownloadSeedTask;
import com.sfc.download.model.po.DownloadTaskInfo;
import com.sfc.download.repo.DownloadSeedTaskRepo;
import com.sfc.download.repo.DownloadTaskRepo;
import com.sfc.download.service.SshCredentialService;
import com.sfc.download.service.impl.DownloadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下载任务列表「做种保留」展示字段填充的单元测试。
 *
 * <p>覆盖：{@code DownloadTaskInfo} 的 transient 字段 {@code seeding / seedEndAt} 由
 * {@link SeedTaskStatus#ACTIVE_STATUSES} 范围内的做种记录批量填充；查询失败时降级为「未做种」。</p>
 *
 * <p>被测服务依赖较多，这里只把与本用例相关的两个仓储做成 Mockito 替身，其余依赖用空替身满足构造器
 * （列表查询路径不会调用它们）。</p>
 */
@ExtendWith(MockitoExtension.class)
class DownloadServiceSeedingFillTest {

    /**
     * 下载任务仓储替身。
     */
    @Mock
    private DownloadTaskRepo downloadTaskRepo;

    /**
     * 做种记录仓储替身。
     */
    @Mock
    private DownloadSeedTaskRepo downloadSeedTaskRepo;

    /**
     * 被测服务。
     */
    private DownloadServiceImpl downloadService;

    /**
     * 构造被测服务（仅注入本用例相关的两个仓储替身）。
     */
    @BeforeEach
    void setUp() {
        downloadService = new DownloadServiceImpl(
                downloadTaskRepo,
                downloadSeedTaskRepo,
                mock(SfcCoreFileClient.class),
                mock(SfcCoreProxyClient.class),
                mock(SshCredentialService.class),
                mock(SfcTaskWorkerClient.class),
                mock(DownloadEngineRegistry.class),
                mock(SfcDownloadConfigService.class));
    }

    @Test
    @DisplayName("列表查询后按 downloadId 批量填充 seeding / seedEndAt，无记录的任务为 false")
    void shouldFillSeedingFieldsInTaskList() {
        DownloadTaskInfo finished = task(1L, DownloadTaskConstants.Status.FINISH, 10L);
        DownloadTaskInfo waiting = task(2L, DownloadTaskConstants.Status.WAITING, null);
        when(downloadTaskRepo.findByUid(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(finished, waiting)));
        Date seedEndAt = new Date(System.currentTimeMillis() + 600_000L);
        DownloadSeedTask seed = new DownloadSeedTask();
        seed.setId(77L);
        seed.setDownloadId(1L);
        seed.setStatus(SeedTaskStatus.SEEDING);
        seed.setSeedEndAt(seedEndAt);
        when(downloadSeedTaskRepo.findByDownloadIdInAndStatusIn(anyCollection(), eq(SeedTaskStatus.ACTIVE_STATUSES)))
                .thenReturn(List.of(seed));

        Page<DownloadTaskInfo> page = downloadService.getTaskList(9L, 0, 10, TaskType.ALL);

        assertEquals(Boolean.TRUE, page.getContent().getFirst().getSeeding());
        assertEquals(seedEndAt, page.getContent().getFirst().getSeedEndAt());
        assertEquals(Boolean.FALSE, page.getContent().getLast().getSeeding());
        assertNull(page.getContent().getLast().getSeedEndAt());
        // 批量查询必须一次带上本页全部下载任务 id（分批工具负责拆分，这里验证没有逐条查询）
        verify(downloadSeedTaskRepo).findByDownloadIdInAndStatusIn(
                argThat(ids -> ids != null && ids.size() == 2), eq(SeedTaskStatus.ACTIVE_STATUSES));
    }

    @Test
    @DisplayName("做种记录查询失败时降级：列表接口不失败，本页 seeding 统一为 false")
    void shouldDegradeWhenSeedQueryFails() {
        DownloadTaskInfo finished = task(1L, DownloadTaskConstants.Status.FINISH, 10L);
        when(downloadTaskRepo.findByUid(eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(finished)));
        when(downloadSeedTaskRepo.findByDownloadIdInAndStatusIn(anyCollection(), anyCollection()))
                .thenThrow(new IllegalStateException("database is locked"));

        Page<DownloadTaskInfo> page = downloadService.getTaskList(9L, 0, 10, TaskType.ALL);

        assertEquals(1, page.getContent().size());
        assertEquals(Boolean.FALSE, page.getContent().getFirst().getSeeding());
        assertNull(page.getContent().getFirst().getSeedEndAt());
    }

    /**
     * 构造下载任务。
     *
     * @param id     任务 id
     * @param status 任务状态镜像
     * @param taskId 异步任务 id，允许为 {@code null}
     * @return 下载任务
     */
    private static DownloadTaskInfo task(Long id, Integer status, Long taskId) {
        DownloadTaskInfo task = new DownloadTaskInfo();
        task.setId(id);
        task.setStatus(status);
        task.setTaskId(taskId);
        return task;
    }
}
