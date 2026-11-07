package com.sfc.download.engine.proxy;

import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory2;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.util.Objects;

/**
 * 固定使用指定代理的 JGit HTTP 连接工厂。
 *
 * <p><b>为什么需要它：</b>JGit 的 {@code TransportHttp} 在构造时读取 JVM 默认
 * {@link java.net.ProxySelector} 与全局 {@code HttpTransport} 连接工厂，二者都是进程级共享状态，
 * 无法按任务指定代理（本服务会被 Worker 线程池并发调用，不同任务可能使用不同代理，甚至直连）。
 * 通过 {@code TransportHttp#setHttpConnectionFactory(HttpConnectionFactory)} 在<b>单个 transport</b>
 * 上替换连接工厂，即可把代理收敛到本次克隆，不影响其它并发任务。</p>
 *
 * <p><b>代理类型：</b>JDK 的 {@code HttpURLConnection#openConnection(Proxy)} 同时支持 HTTP 与 SOCKS
 * 代理（本实现直接委托 {@link JDKHttpConnectionFactory#create(URL, Proxy)}，不做类型过滤）。</p>
 *
 * @see com.sfc.download.engine.impl.GitDownloadEngine
 */
public final class FixedProxyHttpConnectionFactory implements HttpConnectionFactory2 {

    /**
     * 实际创建连接的委托工厂（负责 HTTPS 的默认证书校验等行为）。
     */
    private final JDKHttpConnectionFactory delegate = new JDKHttpConnectionFactory();

    /**
     * 本次克隆固定使用的代理。
     */
    private final Proxy proxy;

    /**
     * 构造固定代理的连接工厂。
     *
     * @param proxy 代理对象，不能为 {@code null}
     * @throws NullPointerException 代理为 {@code null} 时抛出
     */
    public FixedProxyHttpConnectionFactory(Proxy proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy 不能为 null");
    }

    /**
     * 创建使用固定代理的连接。
     *
     * @param url 目标地址
     * @return 已绑定固定代理的连接
     * @throws IOException 创建失败时抛出
     */
    @Override
    public HttpConnection create(URL url) throws IOException {
        return delegate.create(url, proxy);
    }

    /**
     * 创建使用固定代理的连接。
     *
     * <p>JGit 传入的 {@code proxy} 来自 JVM 全局 {@code ProxySelector}，本实现刻意忽略它，
     * 以保证本次克隆只使用任务指定的代理。</p>
     *
     * @param url            目标地址
     * @param ignoredProxy   JGit 按全局 ProxySelector 计算出的代理（忽略）
     * @return 已绑定固定代理的连接
     * @throws IOException 创建失败时抛出
     */
    @Override
    public HttpConnection create(URL url, Proxy ignoredProxy) throws IOException {
        return delegate.create(url, proxy);
    }

    /**
     * 创建新的会话状态。
     *
     * <p>直接委托 JDK 工厂：其会话实现负责在「不校验 SSL」场景下配置信任管理器，
     * 本类只替换代理，不改变证书校验语义。</p>
     *
     * @return 新的会话
     */
    @Override
    public GitSession newSession() {
        return delegate.newSession();
    }
}
