package com.sfc.download.model.property;

import com.sfc.common.annotations.ConfigPropertiesGroup;
import com.sfc.common.annotations.ConfigProperty;
import com.sfc.common.annotations.ConfigPropertyEntity;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * aria2 P2P 下载运行期配置实体。
 *
 * <p>配置值统一存放在 {@code sfc-core} 的系统配置表中，由管理员在 core 后台「插件配置」面板维护，
 * 本服务经 {@link com.sfc.download.config.SfcDownloadConfigService} 读取；修改后按配置缓存 TTL
 * （默认 5 秒）生效，无需重启本服务。</p>
 *
 * <p><b>与部署期配置的分界：</b>RPC 地址、令牌与共享目录仍属于部署期配置
 * （{@link com.sfc.download.config.Aria2Properties}，来自 {@code application.yml} / 环境变量，
 * 修改后需重启）；本类只承载 P2P 开关、进度轮询 / 无进度超时与种子体积上限。</p>
 *
 * <p><b>配置项命名：</b>前缀 {@code download.aria2} + KEBAB_CASE 字段名，
 * 最终 key 形如 {@code download.aria2.stall-timeout}。</p>
 *
 * @see com.sfc.download.config.SfcDownloadConfigService
 */
@Slf4j
@Data
@ConfigPropertyEntity(prefix = "download.aria2", groups = {
        @ConfigPropertiesGroup(id = "base", name = "P2P 下载参数")
})
public class Aria2Property {

    /**
     * P2P 下载总开关。
     *
     * <p>默认 {@code false}。关闭时磁力链（MAGNET）与种子（TORRENT）两种下载类型在创建任务阶段即被
     * 拒绝，能力声明接口也会把它们标记为不可用。</p>
     */
    @ConfigProperty(
            title = "启用 P2P 下载",
            describe = "关闭时磁力链与种子在创建任务阶段即被拒绝，能力声明接口也会标记为不可用",
            inputType = "switch",
            defaultValue = "false"
    )
    private Boolean enabled = Boolean.FALSE;

    /**
     * 下载进度轮询间隔（毫秒）。
     *
     * <p>默认 1000。向 aria2 查询下载进度的间隔：过大则进度卡顿，过小则增加 RPC 压力。</p>
     */
    @ConfigProperty(
            title = "进度轮询间隔(毫秒)",
            describe = "向 aria2 查询下载进度的间隔；过大则进度卡顿，过小则增加 RPC 压力",
            defaultValue = "1000"
    )
    private Integer pollInterval = 1000;

    /**
     * 无进度超时（秒）。
     *
     * <p>默认 300。连续该时长内 aria2 上报的已下载字节数没有增长，则判定任务失败，避免种子无资源时
     * 任务长期占用执行槽位。</p>
     *
     * <p><b>不限时长：</b>配置为 {@code 0} 表示<b>不做无进度检测</b>（永久等待），此时任务不会因无进度
     * 自动失败，会一直占用一个下载并发槽位，只能手动中断或等待执行者实例失联被 core 回收。</p>
     */
    @ConfigProperty(
            title = "无进度超时(秒)",
            describe = "连续该时长已下载字节数没有增长即判定任务失败；填 0 表示不限（永久等待、不做无进度检测），"
                    + "此时任务会一直占用下载并发位，只能手动中断",
            defaultValue = "300"
    )
    private Integer stallTimeout = 300;

    /**
     * 种子内容上限（MB）。
     *
     * <p>默认 2。TORRENT 类型允许通过 Base64 直接提交种子文件内容，该上限用于防止超大的请求体被
     * 写入异步任务参数。</p>
     */
    @ConfigProperty(
            title = "种子内容上限(MB)",
            describe = "TORRENT 类型通过 Base64 直接提交种子内容时的体积上限，防止超大请求体写入异步任务参数",
            defaultValue = "2"
    )
    private Integer torrentMaxSize = 2;

    /**
     * 做种保留时长（分钟）。
     *
     * <p>默认 {@code 10080}（7 天）。磁力 / 种子下载完成后保留本地文件做种分享的时长；{@code 0} 表示
     * 不做种（下载完成即清理本地文件与 aria2 任务结果），与改造前的 {@code seed-time=0} 行为一致。</p>
     *
     * <p><b>生效时机：</b>任务提交给 aria2 时读取快照，修改后仅对<b>新任务</b>生效；进行中任务沿用提交时
     * 的值。取值规则见 {@link #resolveSeedTimeMinutes()}。</p>
     */
    @ConfigProperty(
            title = "做种保留时长(分钟)",
            describe = "磁力/种子下载完成后保留本地文件做种分享的时长（分钟）；0=不做种，完成后立即清理本地文件",
            defaultValue = "10080"
    )
    private Integer seedTimeMinutes = 10080;

    /**
     * 做种保留时长的配置上限（分钟）：1 年。
     */
    public static final int MAX_SEED_TIME_MINUTES = 525600;

    /**
     * 解析实际生效的做种保留时长（分钟）。
     *
     * <p>规则：{@code null} 或 {@code <= 0} 一律按 {@code 0}（不做种）处理；超过
     * {@link #MAX_SEED_TIME_MINUTES} 时按上限取值并记录一次 WARN（防止配置误填导致文件长期不清理）。
     * P2P 下载引擎（提交 aria2 时）与做种生命周期（计算到期时间时）都必须使用本方法，保证两处取值一致。
     * </p>
     *
     * @return 生效的做种时长（分钟），范围 {@code [0, 525600]}
     */
    public int resolveSeedTimeMinutes() {
        Integer configured = seedTimeMinutes;
        if (configured == null || configured <= 0) {
            return 0;
        }
        if (configured > MAX_SEED_TIME_MINUTES) {
            log.warn("做种保留时长配置超过上限，按上限生效: configured={}分钟, limit={}分钟",
                    configured, MAX_SEED_TIME_MINUTES);
            return MAX_SEED_TIME_MINUTES;
        }
        return configured;
    }
}
