package com.sfc.download.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * aria2（P2P 下载）部署期连接与共享目录配置。
 *
 * <p>本类只保留与部署环境相关的参数：RPC 地址、令牌，以及跨容器部署时的共享目录映射。全部字段来自
 * 本服务的<b>部署配置</b>（{@code application.yml} 的 {@code sfc.download.aria2} 节点，或对应的
 * {@code SFC_DOWNLOAD_ARIA2_*} 环境变量），启动时绑定一次，<b>修改后必须重启本服务</b>才生效。</p>
 *
 * <p><b>运行参数不在这里：</b>P2P 总开关、进度轮询间隔、无进度超时与种子体积上限已迁移到
 * {@code sfc-core} 的统一配置（{@link com.sfc.download.model.property.Aria2Property}，管理员在后台
 * 「插件配置」面板维护，改完最长 5 秒生效，无需重启本服务），避免出现「两处取值、排障时无法判断谁生效」
 * 的双数据源问题。</p>
 *
 * <p><b>令牌安全：</b>{@link #getSecret()} 标注了 {@code @ToString.Exclude}，任何日志、接口响应与
 * 诊断信息都不得输出令牌明文。</p>
 *
 * @see com.sfc.download.model.property.Aria2Property
 * @see com.sfc.download.engine.p2p.Aria2Workspace
 */
@Data
@Component
@ConfigurationProperties(prefix = "sfc.download.aria2")
public class Aria2Properties {

    /**
     * RPC 地址为空时使用的默认地址（与 aria2c 默认监听端口一致）。
     */
    public static final String DEFAULT_RPC_URL = "http://127.0.0.1:6800/jsonrpc";

    /**
     * aria2 JSON-RPC 的完整地址（部署期配置，修改后需重启）。
     *
     * <p>默认 {@code http://127.0.0.1:6800/jsonrpc}，需与 aria2c 的 {@code --rpc-listen-port} 对应；
     * 容器部署时应填写 aria2 容器的可达地址。可通过环境变量 {@code SFC_DOWNLOAD_ARIA2_RPC_URL}
     * 覆盖。留空时回落到 {@link #DEFAULT_RPC_URL}。</p>
     */
    private String rpcUrl = DEFAULT_RPC_URL;

    /**
     * aria2 RPC 令牌（部署期配置，修改后需重启）。
     *
     * <p>默认空串，表示 aria2 未设置 {@code --rpc-secret}。与 aria2c 的启动参数
     * {@code --rpc-secret=<令牌>} 保持一致，可通过环境变量 {@code SFC_DOWNLOAD_ARIA2_SECRET} 传入。
     * </p>
     *
     * <p><b>安全：</b>该字段被排除在 {@code toString()} 之外，日志与诊断信息中只允许出现掩码
     * {@code ***}，不得输出明文。</p>
     */
    @ToString.Exclude
    private String secret = "";

    /**
     * 本服务容器内的共享根目录（部署期配置，修改后需重启）。
     *
     * <p>默认空串，表示未启用共享目录、任务产物仍落在本服务 JVM 临时目录
     * （{@code ${java.io.tmpdir}/offline-download}），行为与未接入共享卷时完全一致。配置后，磁力链与
     * 种子任务目录改在 {@code <sharedDir>/offline-download} 下创建，供 aria2 与下载服务跨容器共享。
     * 必须是本服务可见的绝对路径、存在、是目录且可写，可通过环境变量
     * {@code SFC_DOWNLOAD_ARIA2_SHARED_DIR} 覆盖。</p>
     */
    private String sharedDir = "";

    /**
     * aria2 容器内与 {@link #sharedDir} 指向同一份数据的目录（部署期配置，修改后需重启）。
     *
     * <p>默认空串，表示 aria2 与下载服务看到的是同一个路径（同机部署或共享卷挂载到相同路径）。
     * 跨容器部署时填写 aria2 侧的挂载路径，例如下载服务挂载为 {@code /shared/download}、aria2 挂载为
     * {@code /downloads} 时填 {@code /downloads}。必须是 POSIX 绝对路径（以 {@code /} 开头），可通过
     * 环境变量 {@code SFC_DOWNLOAD_ARIA2_REMOTE_DIR} 覆盖。</p>
     */
    private String remoteDir = "";
}
