package com.sfc.download.model.property;

import com.sfc.common.annotations.ConfigPropertiesGroup;
import com.sfc.common.annotations.ConfigProperty;
import com.sfc.common.annotations.ConfigPropertyEntity;
import lombok.Data;

/**
 * Git 克隆（GIT 下载类型）运行期配置实体。
 *
 * <p>与 {@link Aria2Property} 一样，配置值统一存放在 {@code sfc-core} 的系统配置表中，
 * 由管理员在 core 后台维护，本服务经
 * {@link com.sfc.download.config.SfcDownloadConfigService} 读取。</p>
 *
 * <p><b>配置项命名：</b>前缀 {@code download.git} + KEBAB_CASE 字段名，
 * 最终 key 形如 {@code download.git.keep-git-dir}。</p>
 *
 * <p>这里的取值是<b>单次任务的默认值</b>：创建任务时仍可通过任务参数 {@code options}
 * （如 {@code gitDepth}、{@code stripGitDir}）逐次覆盖。</p>
 *
 * @see Aria2Property
 */
@Data
@ConfigPropertyEntity(prefix = "download.git", groups = {
        @ConfigPropertiesGroup(id = "base", name = "克隆默认参数")
})
public class GitProperty {

    /**
     * 默认克隆深度。
     *
     * <p>{@code 0} 表示全量克隆；大于 {@code 0} 时执行浅克隆（只拉取最近若干次提交），
     * 可显著降低大仓库的下载量。</p>
     */
    @ConfigProperty(
            title = "默认克隆深度",
            describe = "0 表示全量克隆；大于 0 时执行浅克隆，只拉取最近若干次提交",
            defaultValue = "0"
    )
    private Integer defaultDepth = 0;

    /**
     * 是否在克隆完成后保留 {@code .git} 目录。
     *
     * <p>默认关闭：网盘场景下用户需要的是工作区文件，保留 {@code .git} 会让目录体积翻倍
     * 且不便浏览。需要保留版本库元数据的场景可打开该开关，或按任务传
     * {@code options.stripGitDir=false}。</p>
     */
    @ConfigProperty(
            title = "保留 .git 目录",
            describe = "关闭时克隆完成后删除 .git，网盘中只保留工作区文件",
            inputType = "switch",
            defaultValue = "false"
    )
    private Boolean keepGitDir = Boolean.FALSE;

    /**
     * 连接超时（秒）。
     *
     * <p>作用于 JGit 与远端建立连接、认证等阶段的等待上限。</p>
     */
    @ConfigProperty(
            title = "连接超时(秒)",
            describe = "JGit 与远端握手、认证阶段的等待上限",
            defaultValue = "30"
    )
    private Integer connectTimeout = 30;
}
