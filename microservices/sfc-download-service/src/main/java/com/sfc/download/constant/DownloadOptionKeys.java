package com.sfc.download.constant;

/**
 * 下载任务「类型专属参数」的键名常量。
 *
 * <p>{@code com.sfc.download.model.DownloadTaskParams#options} 是一个松散的
 * {@code Map<String, Object>}，不同下载类型从其中读取各自的参数。为了让创建任务的客户端、
 * 各下载引擎（Git / aria2）与文档使用<b>同一份键名</b>，把键名集中在本接口中定义，
 * 避免各处硬编码字符串造成拼写漂移。</p>
 *
 * <p><b>取值约定：</b></p>
 * <ul>
 *   <li>调用方传入的 {@code options} 值可能是 JSON 反序列化后的任意类型（{@code Integer}、
 *       {@code Long}、{@code String}、{@code Boolean}），各引擎必须按「宽松转换」读取，
 *       不能直接强转；</li>
 *   <li>本接口只定义键名，不定义校验规则；非法取值由对应引擎在创建期抛出业务异常拒绝。</li>
 * </ul>
 */
public interface DownloadOptionKeys {

    /**
     * Git 分支或标签名。
     *
     * <p>取值类型：{@link String}。为空表示使用仓库的默认分支（远端 HEAD）。</p>
     */
    String GIT_BRANCH = "gitBranch";

    /**
     * Git 克隆深度（浅克隆层数）。
     *
     * <p>取值类型：{@link Integer} 或 {@link String} 形式的整数。{@code 0} 或不传表示完整克隆；
     * 大于 {@code 0} 时执行浅克隆（{@code --depth}），可显著降低大仓库的下载量与耗时。</p>
     */
    String GIT_DEPTH = "gitDepth";

    /**
     * 是否同时克隆子模块。
     *
     * <p>取值类型：{@link Boolean} 或字符串 {@code "true"}/{@code "false"}。默认 {@code false}。</p>
     */
    String GIT_SUBMODULES = "gitSubmodules";

    /**
     * 是否删除克隆产物中的 {@code .git} 目录。
     *
     * <p>取值类型：{@link Boolean} 或字符串 {@code "true"}/{@code "false"}。默认取服务端统一配置
     * {@code download.git.keep-git-dir} 的反值；本参数存在时以任务参数为准。</p>
     */
    String STRIP_GIT_DIR = "stripGitDir";

    /**
     * 私有仓库的 Git 用户名（仅 HTTP/HTTPS 协议有效）。
     *
     * <p>取值类型：{@link String}。与 {@link #GIT_TOKEN} 成对使用；<b>禁止写入日志与接口响应</b>。</p>
     */
    String GIT_USERNAME = "gitUsername";

    /**
     * 私有仓库的 Git 访问令牌或密码（仅 HTTP/HTTPS 协议有效）。
     *
     * <p>取值类型：{@link String}。与 {@link #GIT_USERNAME} 组成 Basic Auth；<b>禁止写入日志与接口响应</b>。</p>
     */
    String GIT_TOKEN = "gitToken";

    /**
     * 透传给 aria2 的下载参数。
     *
     * <p>取值类型：{@link String}，内容为 JSON 对象文本，例如 <code>{"split":"16"}</code>；对象内的键为
     * aria2 的原始选项名（如 {@code split}、{@code max-connection-per-server}、{@code bt-tracker}），
     * 值统一按字符串处理，空白文本表示不使用任何透传选项。服务端只放行白名单内的选项，
     * 且会强制覆盖 {@code dir}、{@code out} 等涉及本地路径的选项，防止越权写盘。</p>
     */
    String ARIA2_OPTIONS = "aria2Options";
}
