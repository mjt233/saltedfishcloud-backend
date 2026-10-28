package com.sfc.download.model;

import com.sfc.cloud.data.validation.SfcUID;
import com.sfc.common.model.ConfigNode;
import com.sfc.download.engine.DownloadType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpMethod;

import java.util.Map;

/**
 * 离线下载任务参数。
 *
 * <p>该对象有两个使用场景：</p>
 * <ul>
 *   <li><b>创建任务</b>：由 {@code POST /api/task/download} 的请求体反序列化而来；</li>
 *   <li><b>任务执行</b>：序列化后作为异步任务参数提交给 core，由本服务的任务处理器反序列化执行。</li>
 * </ul>
 *
 * <p>其中 {@code downloadId}、{@code uid}、{@code proxyType}/{@code proxyAddress}/{@code proxyPort}
 * 的部分取值由服务端在创建任务时回填/快照，客户端无需（也不应）传入：</p>
 * <ul>
 *   <li>{@code downloadId}：下载任务主键，用于任务执行时回写进度与状态；</li>
 *   <li>{@code proxyType}/{@code proxyAddress}/{@code proxyPort}：按 {@code proxy} 名称从 core
 *       解析出的代理地址快照，避免执行时 core 侧代理配置已被删除。</li>
 * </ul>
 *
 * <p><b>下载类型：</b>{@link #type} 为<b>必填项</b>，创建任务时必须显式指定，服务端不会按
 * {@link #url} 自动推导、也不会降级到其它类型；任务执行阶段同样只读该字段。前端需要「按地址自动选型」
 * 时，先调用类型推断接口取得候选类型，再随请求提交。</p>
 */
public class DownloadTaskParams {

    /**
     * 下载类型（必填）。
     *
     * <p>取值见 {@link DownloadType}，大小写不敏感；为空时创建任务会因 Bean Validation 失败返回 422。
     * 创建任务成功后该字段原样落库并随任务参数提交，执行阶段不再重新推导。</p>
     */
    @NotNull(message = "下载类型不能为空")
    public DownloadType type;

    /**
     * 下载来源地址。
     *
     * <p>含义随 {@link #type} 变化：HTTP 为直链、GIT 为 Git 仓库地址、MAGNET 为磁力链、
     * ED2K 为 ed2k 链接、TORRENT 为 {@code .torrent} 文件地址（也可改用 {@link #torrentBase64}）。</p>
     *
     * <p>格式校验在创建任务阶段按类型进行（见下载来源校验器），本字段本身不做
     * Bean Validation 约束，以免磁力链、ed2k 链接被 URL 校验器误拒。</p>
     */
    public String url;

    /**
     * 类型专属选项。
     *
     * <p>键由各下载引擎自行读取与校验；能力声明接口用 {@link ConfigNode} 给出每个键的展示名、
     * 输入控件类型、默认值与说明。已支持的键：</p>
     * <ul>
     *   <li><b>GIT</b>：{@code gitBranch}（分支）、{@code gitDepth}（克隆深度，0 表示全量）、
     *       {@code gitSubmodules}（是否克隆子模块）、{@code stripGitDir}（是否删除 {@code .git} 目录）、
     *       {@code gitUsername} / {@code gitToken}（私有仓库凭证）；</li>
     *   <li><b>MAGNET / TORRENT</b>：{@code aria2Options}（透传给 aria2 的选项白名单，
     *       取值为 JSON 对象文本字符串，例如 <code>{"split":"16"}</code>）。</li>
     * </ul>
     *
     * <p>{@code gitUsername} 与 {@code gitToken} 属于凭证，不会出现在日志与参数摘要中。</p>
     */
    public Map<String, Object> options;

    /**
     * Base64 编码的种子文件内容（仅 {@link DownloadType#TORRENT} 使用）。
     *
     * <p>与 {@link #url} 二选一：前端上传 {@code .torrent} 文件后把内容编码为 Base64 放在这里，
     * 即可在无法提供可访问地址时创建种子下载任务。大小上限由配置项控制。</p>
     */
    public String torrentBase64;

    /**
     * 下载任务主键（服务端创建任务后回填，客户端传入无效）。
     */
    public Long downloadId;

    /**
     * 资源所属用户 uid（保存到哪个用户的网盘）。
     *
     * <p>{@code @SfcUID(value = true)} 表示公共资源（uid = 0）也仅管理员可写。</p>
     */
    @SfcUID(value = true)
    public long uid;

    /**
     * 发起下载请求时附加的请求头（会与默认 User-Agent 合并，仅 HTTP 下载使用）。
     */
    public Map<String, String> headers;

    /**
     * 发起下载请求使用的 HTTP 方法，默认 {@code GET}（仅 HTTP 下载使用）。
     */
    public String method = HttpMethod.GET.name();

    /**
     * 使用的代理名称（在 core 的代理配置中维护）；为空表示直连。
     */
    public String proxy;

    /**
     * 代理类型（HTTP / SOCKS），由服务端按 {@link #proxy} 解析后快照。
     */
    public String proxyType;

    /**
     * 代理地址，由服务端按 {@link #proxy} 解析后快照。
     */
    public String proxyAddress;

    /**
     * 代理端口，由服务端按 {@link #proxy} 解析后快照。
     */
    public Integer proxyPort;

    /**
     * 连接超时时间（毫秒），默认 10 秒。
     */
    public int connectTimeout = 10000;

    /**
     * 读取超时时间（毫秒），默认 60 秒。
     */
    public int readTimeout = 60000;

    /**
     * 保存到网盘的目录路径。
     */
    @NotEmpty
    public String savePath;

    /**
     * 生成用于日志输出的参数摘要。
     *
     * <p>刻意不输出以下内容，避免把用户凭证写入日志：{@code headers} 的逐项内容、
     * {@code options} 的逐项内容（含 {@code gitUsername}/{@code gitToken}）、{@code torrentBase64} 的内容。</p>
     *
     * @return 参数摘要
     */
    @Override
    public String toString() {
        return "DownloadTaskParams{" +
                "type=" + (type == null ? "未指定" : type.getCode()) +
                ", url='" + url + '\'' +
                ", downloadId=" + downloadId +
                ", uid=" + uid +
                ", headers=" + (headers == null ? 0 : headers.size()) + " entries" +
                ", options=" + (options == null ? 0 : options.size()) + " entries" +
                ", torrentBase64=" + (torrentBase64 == null || torrentBase64.isEmpty() ? "无" : "已提供") +
                ", method='" + method + '\'' +
                ", proxy='" + proxy + '\'' +
                ", savePath='" + savePath + '\'' +
                '}';
    }
}
