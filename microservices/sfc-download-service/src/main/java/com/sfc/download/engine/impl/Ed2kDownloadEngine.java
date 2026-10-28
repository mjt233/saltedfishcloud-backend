package com.sfc.download.engine.impl;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.ConfigNode;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.constant.DownloadTaskConstants;
import com.sfc.download.engine.DownloadArtifact;
import com.sfc.download.engine.DownloadContext;
import com.sfc.download.engine.DownloadEngine;
import com.sfc.download.engine.DownloadType;
import com.sfc.download.model.DownloadTaskParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ed2k 下载引擎（<b>占位实现</b>）。
 *
 * <p><b>为什么是占位：</b>aria2 原生只支持 BitTorrent（磁力链 / 种子），<b>不支持 ed2k</b>；
 * Java 生态也没有可用的 ed2k 协议库。因此本期只把 {@link DownloadType#ED2K} 登记为「已知类型」，
 * 在能力声明接口中以「不可用 + 原因」呈现，并在创建任务阶段直接返回明确的 400，
 * 避免用户提交一个注定失败的任务去占用异步任务队列与执行槽位。</p>
 *
 * <p><b>后续接入点（只改这一个类，无需动 Handler / Service / Controller）：</b></p>
 * <ul>
 *   <li><b>mldonkey</b>：以守护进程方式运行，通过 telnet 端口（默认 4000）用纯文本命令
 *       {@code dllink <ed2k链接>} 提交，{@code vd} 查看下载列表；协议简单，推荐优先接入；</li>
 *   <li><b>aMule</b>：通过 {@code amulecmd} 的 EC（External Connections）协议提交，为二进制协议，
 *       通常还需要 {@code amuleweb}，部署与实现成本更高。</li>
 * </ul>
 *
 * <p>接入时只需：把 {@link #unavailableReason()} 改为按配置探测外部程序是否可用、
 * 在 {@link #execute(DownloadContext)} 中实现提交与轮询，并把产物写入
 * {@link DownloadContext#getTempDir()} 后返回 {@link DownloadArtifact} 列表即可。</p>
 */
@Slf4j
@Component
public class Ed2kDownloadEngine implements DownloadEngine {

    /**
     * 日志前缀，与下载任务其它组件保持一致。
     */
    private static final String LOG_PREFIX = DownloadTaskConstants.LOG_PREFIX;

    /**
     * 能力声明与创建任务阶段返回给用户的统一文案。
     */
    private static final String UNAVAILABLE_REASON =
            "ed2k 下载引擎尚未接入（预留扩展点，可接入 mldonkey / aMule）";

    /**
     * 创建任务/执行阶段拒绝时的提示文案。
     */
    private static final String REJECT_MESSAGE =
            "ed2k 下载引擎尚未接入，当前支持 HTTP / GIT / MAGNET / TORRENT";

    /**
     * {@inheritDoc}
     *
     * @return {@code "ed2k"}
     */
    @Override
    public String getEngineId() {
        return "ed2k";
    }

    /**
     * {@inheritDoc}
     *
     * @return 仅 {@link DownloadType#ED2K}
     */
    @Override
    public Set<DownloadType> supports() {
        return Set.of(DownloadType.ED2K);
    }

    /**
     * {@inheritDoc}
     *
     * <p>按 {@code ed2k://} 前缀判断（大小写不敏感），不校验链接完整性——即使引擎尚未接入，也要让前端
     * 能正确选中 ed2k 类型并看到「尚未接入」的提示。</p>
     */
    @Override
    public boolean matchesSource(DownloadType type, String source) {
        return type == DownloadType.ED2K
                && SfcStringUtils.hasText(source)
                && source.trim().toLowerCase(Locale.ROOT).startsWith("ed2k://");
    }

    /**
     * {@inheritDoc}
     *
     * @return 固定的「尚未接入」说明，使 ed2k 在能力声明接口中稳定呈现为不可用
     */
    @Override
    public String unavailableReason() {
        return UNAVAILABLE_REASON;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "ed2k 下载"}
     */
    @Override
    public String taskName() {
        return "ed2k 下载";
    }

    /**
     * {@inheritDoc}
     *
     * <p>占位实现没有专属选项。</p>
     *
     * @return 空列表
     */
    @Override
    public List<ConfigNode> options() {
        return List.of();
    }

    /**
     * {@inheritDoc}
     *
     * <p>正常情况下本方法不会被调用：{@link com.sfc.download.engine.DownloadEngineRegistry#validate}
     * 会先检查 {@link #unavailableReason()} 并返回 400。这里保留同样的拒绝语义，作为防御性兜底。</p>
     *
     * @param params 下载任务参数
     * @throws SfcJsonException 始终抛出（HTTP 400 语义）
     */
    @Override
    public void validate(DownloadTaskParams params) {
        log.warn("{}收到 ed2k 下载校验请求，但引擎尚未接入", LOG_PREFIX);
        throw new SfcJsonException(400, REJECT_MESSAGE);
    }

    /**
     * {@inheritDoc}
     *
     * <p>与 {@link #validate(DownloadTaskParams)} 一致，属于防御性兜底：即使有历史任务绕过创建期校验
     * 进入执行阶段，也会以明确的业务错误失败，而不是产生不可解释的异常。</p>
     *
     * @param context 执行上下文
     * @return 永不返回
     * @throws SfcJsonException 始终抛出（HTTP 400 语义）
     */
    @Override
    public List<DownloadArtifact> execute(DownloadContext context) {
        log.warn("{}收到 ed2k 下载执行请求，但引擎尚未接入: downloadId={}",
                LOG_PREFIX, context.getDownloadId());
        context.log("ed2k 下载引擎尚未接入，任务终止");
        throw new SfcJsonException(400, REJECT_MESSAGE);
    }
}
