package com.sfc.download.model;

import com.sfc.cloud.data.validation.SfcUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.URL;
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
 */
public class DownloadTaskParams {

    /**
     * 要下载的文件 URL。
     */
    @URL
    @NotBlank
    public String url;

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
     * 发起下载请求时附加的请求头（会与默认 User-Agent 合并）。
     */
    public Map<String, String> headers;

    /**
     * 发起下载请求使用的 HTTP 方法，默认 {@code GET}。
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
     * <p>刻意不输出 {@code headers} 的逐项内容，避免把用户自定义请求头（可能含凭证）写入日志。</p>
     *
     * @return 参数摘要
     */
    @Override
    public String toString() {
        return "DownloadTaskParams{" +
                "url='" + url + '\'' +
                ", downloadId=" + downloadId +
                ", uid=" + uid +
                ", headers=" + (headers == null ? 0 : headers.size()) + " entries" +
                ", method='" + method + '\'' +
                ", proxy='" + proxy + '\'' +
                ", savePath='" + savePath + '\'' +
                '}';
    }
}
