package com.sfc.download.model.property;

import com.sfc.common.annotations.ConfigPropertiesGroup;
import com.sfc.common.annotations.ConfigProperty;
import com.sfc.common.annotations.ConfigPropertyEntity;
import lombok.Data;

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
}
