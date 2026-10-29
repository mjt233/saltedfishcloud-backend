package com.sfc.download.util;

import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.download.model.DownloadTaskParams;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Locale;

/**
 * 下载任务的代理装配工具。
 *
 * <p>下载任务在创建期由服务端把 core 代理节点快照成 {@code proxyType}/{@code proxyAddress}/{@code proxyPort}，
 * 执行期各引擎据此构造 {@link Proxy}。本类把这套「快照 → Proxy」的解析与校验收敛到一处，
 * 供 Git 引擎（HTTP(S) 传输与 SSH 传输）复用，避免各引擎各写一份解析逻辑导致行为漂移。</p>
 *
 * <p><b>代理类型：</b>支持 {@code HTTP}（含 {@code HTTPS} 别名）与 {@code SOCKS}/{@code SOCKS5}。
 * 未指定类型时按 HTTP 代理处理（与 core 代理实体的默认语义一致）。</p>
 */
public final class DownloadProxyUtils {

    /**
     * 代理端口合法范围下限。
     */
    private static final int MIN_PROXY_PORT = 1;

    /**
     * 代理端口合法范围上限。
     */
    private static final int MAX_PROXY_PORT = 65535;

    /**
     * 工具类禁止实例化。
     */
    private DownloadProxyUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 根据任务参数中的代理快照构造 {@link Proxy}。
     *
     * <p>未指定代理时返回 {@code null}（直连）；指定了代理但快照字段缺失或端口非法时按参数错误处理，
     * 避免带着错误的代理信息发起网络请求。</p>
     *
     * @param params 下载任务参数，允许为 {@code null}
     * @return 代理对象；未配置代理时返回 {@code null}
     * @throws SfcJsonException 代理快照不完整、端口非法或类型无法识别时抛出（HTTP 400 语义）
     */
    public static Proxy toProxy(DownloadTaskParams params) {
        if (params == null) {
            return null;
        }
        if (!SfcStringUtils.hasText(params.proxyAddress)) {
            if (SfcStringUtils.hasText(params.proxy)) {
                throw new SfcJsonException(400, "无效的代理配置: 缺少代理地址快照（" + params.proxy + "）");
            }
            return null;
        }
        if (params.proxyPort == null || params.proxyPort < MIN_PROXY_PORT || params.proxyPort > MAX_PROXY_PORT) {
            throw new SfcJsonException(400, "无效的代理端口: " + params.proxyPort);
        }
        return new Proxy(parseType(params.proxyType), new InetSocketAddress(params.proxyAddress, params.proxyPort));
    }

    /**
     * 解析代理协议类型。
     *
     * @param proxyType 代理类型字符串（来自 core 代理快照，取值 {@code HTTP}/{@code SOCKS}）
     * @return 对应的 {@link Proxy.Type}
     * @throws SfcJsonException 类型无法识别时抛出（HTTP 400 语义）
     */
    private static Proxy.Type parseType(String proxyType) {
        if (!SfcStringUtils.hasText(proxyType)) {
            return Proxy.Type.HTTP;
        }
        return switch (proxyType.trim().toUpperCase(Locale.ROOT)) {
            case "SOCKS", "SOCKS5" -> Proxy.Type.SOCKS;
            case "HTTP", "HTTPS" -> Proxy.Type.HTTP;
            default -> throw new SfcJsonException(400, "无效的代理类型: " + proxyType);
        };
    }

    /**
     * 生成用于任务日志的代理描述。
     *
     * @param params 下载任务参数，允许为 {@code null}
     * @param proxy  已解析的代理，允许为 {@code null}（表示直连）
     * @return 可读描述，永不为 {@code null}
     */
    public static String describe(DownloadTaskParams params, Proxy proxy) {
        if (proxy == null) {
            return "直连";
        }
        String name = params == null ? null : params.proxy;
        String address = params == null ? null : params.proxyAddress;
        Integer port = params == null ? null : params.proxyPort;
        return (SfcStringUtils.hasText(name) ? name + " - " : "")
                + proxy.type() + "://" + address + ":" + port;
    }
}
