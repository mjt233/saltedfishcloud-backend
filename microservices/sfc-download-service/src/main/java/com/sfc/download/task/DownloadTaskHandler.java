package com.sfc.download.task;

import com.sfc.cloud.exception.SfcRemoteApiException;
import com.sfc.cloud.model.SfcFileInfo;
import com.sfc.cloud.remote.client.SfcCoreFileClient;
import com.sfc.cloud.task.worker.SfcTaskExecution;
import com.sfc.cloud.task.worker.SfcTaskHandler;
import com.sfc.cloud.task.worker.SfcTaskInterruptedException;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcMapperHolder;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadEngine;
import com.sfc.download.engine.DownloadEngineRegistry;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.model.DownloadTaskParams;
import com.sfc.download.repo.DownloadTaskRepo;
import com.sfc.download.stash.DownloadSaveService;
import com.sfc.download.util.DownloadFileUtils;
import com.sfc.download.util.DownloadLogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 离线下载任务处理器（任务类型 {@code offline-download}）。
 *
 * <p>由 sfc-task-worker SDK 在认领到 {@link DownloadTaskConstants#TASK_TYPE} 类型的任务后调用
 * {@link #execute(SfcTaskExecution)}。本类只负责「调度与状态机」，<b>不包含任何具体的下载实现</b>：</p>
 * <ol>
 *     <li>解析任务参数 {@link DownloadTaskParams}，把本地任务状态镜像置为运行中；</li>
 *     <li>把 Worker 的中断指令接线到 {@link DownloadContext}，使引擎能够及时释放阻塞资源；</li>
 *     <li>通过 {@link SfcCoreFileClient#list(long, String)} 校验保存目录是否存在；</li>
 *     <li>按参数类型经 {@link DownloadEngineRegistry} 路由到具体下载引擎（HTTP / Git / 磁力链 / 种子…）；</li>
 *     <li>引擎执行完毕后，由 {@link DownloadSaveService} 统一把产物写入 core 网盘；</li>
 *     <li>回写完成状态；异常时按「取消 / 失败」两种语义回写，并在 {@code finally} 中清理临时目录。</li>
 * </ol>
 *
 * <p><b>扩展方式：</b>新增一种下载方式只需要实现 {@link DownloadEngine} 并注册为 Spring Bean，
 * 本类与业务接口都无需改动——这正是本次改造把「下载执行」从处理器中抽离出来的目的。</p>
 *
 * <p><b>并发说明：</b>本处理器是 Spring 单例，会被 Worker 线程池并发调用，因此自身不持有任何
 * 任务级可变状态；一次执行的全部可变状态都保存在每次新建的 {@link DownloadContext} 中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadTaskHandler implements SfcTaskHandler {

    /**
     * 日志前缀：应用日志与任务运行日志共用，便于按前缀检索。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 写入 {@code download_task.message} 的错误信息最大长度，避免超长堆栈撑爆数据库行。
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    /**
     * 下载任务仓储：回写本地任务状态镜像（状态、大小、保存名、结束时间、失败信息）。
     */
    private final DownloadTaskRepo downloadTaskRepo;

    /**
     * core 网盘文件客户端：仅用于校验保存目录是否存在且当前用户可访问。
     */
    private final SfcCoreFileClient fileClient;

    /**
     * 下载引擎注册表：按下载类型路由到具体引擎。
     */
    private final DownloadEngineRegistry engineRegistry;

    /**
     * 下载产物落盘服务：把引擎产出的本地文件写入 core 网盘。
     */
    private final DownloadSaveService downloadSaveService;

    /**
     * 本处理器支持的任务类型。
     *
     * @return {@link DownloadTaskConstants#TASK_TYPE}
     */
    @Override
    public String taskType() {
        return DownloadTaskConstants.TASK_TYPE;
    }

    /**
     * 执行一个离线下载任务。
     *
     * <p><b>执行顺序：</b>参数解析 → 置运行中 → 中断接线 → 保存目录校验 → 类型路由 → 引擎执行 →
     * 落盘 → 置完成。</p>
     *
     * <p><b>异常映射约定：</b></p>
     * <ul>
     *     <li>中断（{@link SfcTaskInterruptedException}，或中断指令导致 IO 失败）→ 本地状态置为
     *         {@link DownloadTaskConstants.Status#CANCEL} 后以中断语义抛出，由 SDK 上报 CANCEL；</li>
     *     <li>其它异常 → 本地状态置为 {@link DownloadTaskConstants.Status#FAILED} 并记录错误信息后
     *         原样抛出，由 SDK 上报 FAILED；</li>
     *     <li>无论成功失败，{@code finally} 中都会删除本地临时目录。</li>
     * </ul>
     *
     * @param execution 任务执行上下文（任务参数、日志、进度与中断协作）
     * @throws SfcTaskInterruptedException 任务被中断时抛出（由 SDK 上报为 CANCEL）
     * @throws RuntimeException           其它执行失败（由 SDK 上报为 FAILED）
     */
    @Override
    public void execute(SfcTaskExecution execution) {
        // 参数解析刻意放在 try 之前：此时还拿不到 downloadId，无法回写本地状态镜像，
        // 解析失败直接由 SDK 上报失败即可。
        DownloadTaskParams params = parseParams(execution);
        Long downloadId = params.downloadId;
        Long taskId = execution.getTaskId();
        long startNanos = System.nanoTime();
        DownloadContext context = new DownloadContext(execution, params);
        try {
            execution.log(LOG_PREFIX + "开始执行离线下载任务: " + params);
            log.info("{}开始执行离线下载任务: downloadId={}, taskId={}, uid={}, type={}, url={}, savePath={}",
                    LOG_PREFIX, downloadId, taskId, params.uid,
                    params.type == null ? "未指定" : params.type.getCode(),
                    DownloadLogUtils.maskUrl(params.url), params.savePath);
            downloadTaskRepo.updateStatus(downloadId, DownloadTaskConstants.Status.RUNNING);

            // 先接线再检查：既覆盖「接线前就已收到取消」的情况，也让引擎在自身注册取消回调时能立即感知
            execution.onInterrupt(context::cancel);
            context.checkInterrupted();

            // 类型只取创建任务时显式指定并落库的值：执行期不做任何推导或降级
            DownloadType type = params.type;
            if (type == null) {
                throw new SfcJsonException(400, "离线下载任务参数缺少下载类型（type），拒绝执行");
            }
            DownloadEngine engine = engineRegistry.get(type);

            validateSavePath(context);

            context.log("使用下载方式: " + type.getDisplayName());
            log.info("{}下载任务已路由到引擎: downloadId={}, taskId={}, type={}, engineId={}",
                    LOG_PREFIX, downloadId, taskId, type.getCode(), engine.getEngineId());

            List<DownloadArtifact> artifacts = engine.execute(context);
            log.info("{}下载引擎执行完成: downloadId={}, taskId={}, type={}, 产物数={}",
                    LOG_PREFIX, downloadId, taskId, type.getCode(), artifacts == null ? 0 : artifacts.size());

            // 落盘前再检查一次，避免把已被取消的内容写入网盘
            context.checkInterrupted();
            DownloadSaveService.StashResult result = stash(context, artifacts);

            downloadTaskRepo.updateFinish(downloadId, DownloadTaskConstants.Status.FINISH,
                    result.totalBytes(), result.totalBytes(), result.savedName(), new Date());
            log.info("{}离线下载任务执行完成: downloadId={}, taskId={}, type={}, 保存名={}, 大小={}字节, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, type.getCode(), result.savedName(), result.totalBytes(),
                    elapsedMillis(startNanos));
        } catch (SfcTaskInterruptedException e) {
            execution.log(LOG_PREFIX + "任务已被取消: " + describe(e));
            log.info("{}离线下载任务已取消: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, describe(e), elapsedMillis(startNanos));
            updateCancelStatusQuietly(execution, downloadId);
            throw e;
        } catch (Exception e) {
            if (context.isCancelled() || execution.isInterruptRequested()) {
                // 中断回调会关闭下载流、终止外部下载任务，底层可能抛出任意异常类型，这里统一还原为「取消」语义
                String reason = describe(e);
                execution.log(LOG_PREFIX + "任务因中断而终止: " + reason);
                log.info("{}离线下载任务因中断而终止: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                        LOG_PREFIX, downloadId, taskId, reason, elapsedMillis(startNanos));
                updateCancelStatusQuietly(execution, downloadId);
                throw new SfcTaskInterruptedException("离线下载任务被中断: " + reason, e);
            }
            String message = describe(e);
            execution.log(LOG_PREFIX + "任务执行失败: " + message);
            // 异常堆栈由 Worker SDK 统一以 ERROR 级别输出，这里只补一条带任务上下文的应用日志
            log.warn("{}离线下载任务执行失败: downloadId={}, taskId={}, 原因={}, 总耗时={}ms",
                    LOG_PREFIX, downloadId, taskId, message, elapsedMillis(startNanos));
            updateFailureQuietly(execution, downloadId, message);
            throw e;
        } finally {
            cleanup(context);
        }
    }

    // ==================== 执行步骤 ====================

    /**
     * 解析任务参数。
     *
     * <p>只做「必须能拿到任务主键」的最小校验：参数为空、JSON 非法、缺少 {@code downloadId}
     * 时直接抛 {@link SfcJsonException}（此时无法回写本地状态镜像）；其余参数校验在创建任务阶段
     * 已由 {@link DownloadEngineRegistry#validate(DownloadTaskParams)} 完成。</p>
     *
     * @param execution 任务执行上下文
     * @return 解析后的任务参数
     * @throws SfcJsonException 参数为空、格式非法或缺少 {@code downloadId} 时抛出
     */
    private DownloadTaskParams parseParams(SfcTaskExecution execution) {
        String rawParams = execution.getParams();
        if (!SfcStringUtils.hasText(rawParams)) {
            throw new SfcJsonException(400, "离线下载任务参数为空");
        }
        DownloadTaskParams params;
        try {
            params = SfcMapperHolder.parseJson(rawParams, DownloadTaskParams.class);
        } catch (IOException e) {
            throw new SfcJsonException(400, "离线下载任务参数解析失败: " + e.getMessage());
        }
        if (params == null) {
            throw new SfcJsonException(400, "离线下载任务参数为空");
        }
        if (params.downloadId == null) {
            throw new SfcJsonException(400, "离线下载任务参数缺少 downloadId");
        }
        return params;
    }

    /**
     * 通过 core 的内部文件接口校验保存目录是否存在且可访问。
     *
     * <p>任何 core 侧的失败（路径不存在、非法路径、无权限）都归一为「无效的保存路径」业务异常；
     * 这里只做存在性校验，真正的落盘由 {@link DownloadSaveService} 在下载完成后执行。</p>
     *
     * @param context 任务执行上下文
     * @throws SfcJsonException 保存目录不存在或无权访问时抛出（HTTP 400 语义）
     */
    private void validateSavePath(DownloadContext context) {
        DownloadTaskParams params = context.getParams();
        context.log("校验保存目录: uid=" + params.uid + ", path=" + params.savePath);
        try {
            SfcJsonResult<List<SfcFileInfo>> result = fileClient.list(params.uid, params.savePath);
            if (result == null) {
                throw new SfcJsonException(400, "无效的保存路径:" + params.savePath);
            }
            result.requireSuccess();
        } catch (SfcRemoteApiException e) {
            log.warn("{}校验保存目录失败: downloadId={}, uid={}, path={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), params.uid, params.savePath, e.getMessage());
            throw new SfcJsonException(400, "无效的保存路径:" + params.savePath);
        }
        context.log("保存目录校验通过");
        log.info("{}保存目录校验通过: downloadId={}, uid={}, path={}",
                LOG_PREFIX, context.getDownloadId(), params.uid, params.savePath);
    }

    /**
     * 把引擎产出的产物写入 core 网盘。
     *
     * <p>顶层目录名刻意传 {@code null}：由落盘服务按「全部产物的公共首层目录」推断
     * （Git 仓库为仓库名、多文件种子为种子信息里的名称），单文件场景则直接走单文件通道。</p>
     *
     * @param context   任务执行上下文
     * @param artifacts 下载引擎产出的产物列表，允许为 {@code null}
     * @return 落盘结果（最终保存名与实际上传字节数）
     * @throws SfcJsonException 产物为空、目录创建失败或上传失败时抛出
     */
    private DownloadSaveService.StashResult stash(DownloadContext context, List<DownloadArtifact> artifacts) {
        DownloadTaskParams params = context.getParams();
        long uploadStartNanos = System.nanoTime();
        context.log("开始保存文件到网盘: uid=" + params.uid + ", path=" + params.savePath);
        log.info("{}开始保存文件到网盘: downloadId={}, uid={}, path={}, 产物数={}",
                LOG_PREFIX, context.getDownloadId(), params.uid, params.savePath,
                artifacts == null ? 0 : artifacts.size());
        DownloadSaveService.StashResult result = downloadSaveService.stash(
                new DownloadSaveService.StashRequest(params.uid, params.savePath, null, artifacts));
        context.log("文件保存成功: " + result.savedName() + "（" + result.totalBytes() + " 字节）");
        log.info("{}文件保存成功: downloadId={}, uid={}, path={}, 保存名={}, 大小={}字节, 上传耗时={}ms",
                LOG_PREFIX, context.getDownloadId(), params.uid, params.savePath,
                result.savedName(), result.totalBytes(), elapsedMillis(uploadStartNanos));
        return result;
    }

    /**
     * 任务收尾清理：删除本次任务创建的本地临时目录（递归）。
     *
     * <p>清理属于 best-effort：目录从未创建时会先按需创建再删除（无副作用），任何异常都只记 WARN，
     * 不覆盖原始执行结果。</p>
     *
     * @param context 任务执行上下文
     */
    private void cleanup(DownloadContext context) {
        try {
            Path tempDir = context.getTempDir();
            log.info("{}清理下载临时目录: downloadId={}, path={}",
                    LOG_PREFIX, context.getDownloadId(), tempDir);
            DownloadFileUtils.deleteQuietly(tempDir);
        } catch (RuntimeException e) {
            log.warn("{}清理下载临时目录失败: downloadId={}, cause={}",
                    LOG_PREFIX, context.getDownloadId(), e.toString());
        }
    }

    // ==================== 状态回写与日志工具 ====================

    /**
     * 静默把本地任务状态回写为「已取消」：回写失败只写运行日志，不覆盖原始异常。
     *
     * @param execution  任务执行上下文
     * @param downloadId 下载任务 id
     */
    private void updateCancelStatusQuietly(SfcTaskExecution execution, Long downloadId) {
        try {
            downloadTaskRepo.updateStatus(downloadId, DownloadTaskConstants.Status.CANCEL);
        } catch (RuntimeException e) {
            execution.log(LOG_PREFIX + "回写本地任务状态失败: " + describe(e));
        }
    }

    /**
     * 静默回写本地任务失败信息：回写失败只写运行日志，不覆盖原始异常。
     *
     * @param execution  任务执行上下文
     * @param downloadId 下载任务 id
     * @param message    错误信息
     */
    private void updateFailureQuietly(SfcTaskExecution execution, Long downloadId, String message) {
        try {
            downloadTaskRepo.updateFailure(downloadId, DownloadTaskConstants.Status.FAILED,
                    truncateMessage(message), new Date());
        } catch (RuntimeException e) {
            execution.log(LOG_PREFIX + "回写本地任务失败信息失败: " + describe(e));
        }
    }

    /**
     * 把异常转换为可读的简短描述。
     *
     * @param e 异常，允许为 {@code null}
     * @return 异常描述，永不为 {@code null}
     */
    private static String describe(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        String message = e.getMessage();
        if (!SfcStringUtils.hasText(message) && e.getCause() != null) {
            message = e.getCause().getMessage();
        }
        return SfcStringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }

    /**
     * 截断错误信息到数据库可接受的长度，并避免把代理对（surrogate pair）从中间切开。
     *
     * @param message 原始错误信息，允许为 {@code null}
     * @return 截断后的错误信息
     */
    private static String truncateMessage(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        int end = MAX_ERROR_MESSAGE_LENGTH;
        if (Character.isHighSurrogate(message.charAt(end - 1))) {
            end--;
        }
        return message.substring(0, end) + "...";
    }

    /**
     * 计算从指定纳秒时间戳到当前时刻的耗时。
     *
     * @param startNanos 起始时间（取自 {@link System#nanoTime()}）
     * @return 耗时毫秒数
     */
    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
