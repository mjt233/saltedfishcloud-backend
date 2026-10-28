package com.sfc.download.model.property;

import com.sfc.common.annotations.ConfigPropertiesGroup;
import com.sfc.common.annotations.ConfigProperty;
import com.sfc.common.annotations.ConfigPropertyEntity;
import lombok.Data;
import lombok.ToString;

/**
 * aria2（P2P 下载）运行期配置实体。
 *
 * <p>本类<b>不是</b> Spring 的 {@code @ConfigurationProperties}：配置值统一存放在 {@code sfc-core} 的
 * 系统配置表中，由管理员在 core 后台的「插件配置」面板维护，本服务通过
 * {@link com.sfc.download.config.SfcDownloadConfigService} 读取。</p>
 *
 * <p><b>配置项命名：</b>类上的 {@link ConfigPropertyEntity#prefix()} 为 {@code download.aria2}，
 * 字段名按 KEBAB_CASE 策略转换后拼接，最终 key 形如 {@code download.aria2.rpc-url}。
 * 该 key 与 {@code config-properties.json} 中声明的面板字段同源（都由
 * {@link com.sfc.common.utils.PropertyUtils#getConfigName} 换算），不会出现两处手写 key 漂移。</p>
 *
 * <p><b>字段约定：</b>字段一律使用包装类型，且每个字段的 {@link ConfigProperty#defaultValue()}
 * 必须是可被 {@link com.sfc.common.utils.SfcTypeUtils#convert(Class, Object)} 正确转换的合法字面量
 * ——配置项缺失时即回落到该默认值。</p>
 *
 * @see GitProperty
 * @see com.sfc.download.config.SfcDownloadConfigService
 */
@Data
@ConfigPropertyEntity(prefix = "download.aria2", groups = {
        @ConfigPropertiesGroup(id = "base", name = "连接与超时")
})
public class Aria2Property {

    /**
     * P2P 下载总开关。
     *
     * <p>关闭时磁力链与种子两种下载类型在创建任务阶段即被拒绝，能力声明接口也会把
     * 它们标记为不可用。</p>
     */
    @ConfigProperty(
            title = "启用 aria2",
            describe = "开启后磁力链（MAGNET）与种子（TORRENT）下载类型可用；需要本机已运行 aria2c",
            inputType = "switch",
            defaultValue = "false"
    )
    private Boolean enabled = Boolean.FALSE;

    /**
     * aria2 JSON-RPC 的完整地址。
     */
    @ConfigProperty(
            title = "RPC 地址",
            describe = "aria2 JSON-RPC 的完整地址，需与 aria2c 的 --rpc-listen-port 对应",
            required = true,
            defaultValue = "http://127.0.0.1:6800/jsonrpc"
    )
    private String rpcUrl = "http://127.0.0.1:6800/jsonrpc";

    /**
     * aria2 RPC 令牌。
     *
     * <p>与 {@code aria2c --rpc-secret=<令牌>} 保持一致；留空表示未设置令牌。
     * 该值在后台面板中掩码显示，且日志、接口响应中永不回显（见 {@code toString()}）。</p>
     */
    @ConfigProperty(
            title = "RPC 令牌",
            describe = "与 aria2c 启动参数 --rpc-secret 保持一致，留空表示未设置令牌",
            isMask = true
    )
    @ToString.Exclude
    private String secret = "";

    /**
     * 下载进度轮询间隔（毫秒）。
     */
    @ConfigProperty(
            title = "进度轮询间隔(ms)",
            describe = "向 aria2 查询下载进度的间隔，过大则进度卡顿，过小则增加 RPC 压力",
            defaultValue = "1000"
    )
    private Integer pollInterval = 1000;

    /**
     * 无进度超时（秒）。
     *
     * <p>连续该时长内 aria2 上报的已下载字节数没有增长，则判定任务失败，避免种子无资源时
     * 任务长期占用执行槽位。</p>
     */
    @ConfigProperty(
            title = "无进度超时(秒)",
            describe = "连续该时长没有已下载字节数增长时判定任务失败",
            defaultValue = "300"
    )
    private Integer stallTimeout = 300;

    /**
     * 种子内容上限（MB）。
     *
     * <p>TORRENT 类型允许通过 Base64 直接提交种子文件内容；该上限用于防止超大的请求体
     * 被写入异步任务参数。</p>
     */
    @ConfigProperty(
            title = "种子内容上限(MB)",
            describe = "TORRENT 类型通过 Base64 提交种子内容时的大小上限",
            defaultValue = "2"
    )
    private Integer torrentMaxSize = 2;
}
