package com.sfc.download.util;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * 忽略 SSL 证书校验的 HTTP 请求工厂。
 *
 * <p>离线下载的目标站点经常使用自签名证书或证书链不完整的证书，严格校验会导致下载失败。
 * 本工厂继承 Spring 的 {@link SimpleClientHttpRequestFactory}，在创建 {@link HttpURLConnection} 时
 * 为 HTTPS 连接替换一个「信任所有证书」的 {@link SSLSocketFactory}，从而跳过证书链校验。</p>
 *
 * <p>保留父类的全部配置能力，典型用法：</p>
 * <pre>{@code
 * IgnoreSSLHttpRequestFactory factory = new IgnoreSSLHttpRequestFactory();
 * factory.setConnectTimeout(params.connectTimeout);
 * factory.setReadTimeout(params.readTimeout);
 * if (proxy != null) {
 *     factory.setProxy(proxy);
 * }
 * RestTemplate restTemplate = new RestTemplate(factory);
 * }</pre>
 *
 * <p><strong>安全提示：</strong>本工厂只跳过证书链校验，HTTPS 的域名（Hostname）校验仍由 JDK 的
 * 默认 {@link javax.net.ssl.HostnameVerifier} 负责；因此它仅应用于「下载来源不可信」的离线下载场景，
 * 不可用于需要身份认证的敏感通信。</p>
 */
@Slf4j
public class IgnoreSSLHttpRequestFactory extends SimpleClientHttpRequestFactory {

    /**
     * 所有 TLS 连接共用的「信任所有证书」Socket 工厂。
     *
     * <p>使用静态内部类持有，既保证 JVM 内只初始化一次 SSLContext，又避免类初始化阶段的额外开销。</p>
     */
    private static final class TrustAllSocketFactoryHolder {

        /**
         * 信任所有证书的 Socket 工厂；初始化失败时为 {@code null}（此时回退到 JDK 默认校验）
         */
        private static final SSLSocketFactory INSTANCE = createTrustAllSocketFactory();
    }

    /**
     * 构造一个忽略 SSL 证书校验的请求工厂。
     */
    public IgnoreSSLHttpRequestFactory() {
        super();
    }

    /**
     * 获取「信任所有证书」的 Socket 工厂。
     *
     * @return 信任所有证书的 Socket 工厂；初始化失败时返回 {@code null}
     */
    public static SSLSocketFactory getTrustAllSocketFactory() {
        return TrustAllSocketFactoryHolder.INSTANCE;
    }

    /**
     * 在创建连接后、发起请求前，把 HTTPS 连接的 Socket 工厂替换为「信任所有证书」的实现。
     *
     * @param connection 待初始化的底层连接
     * @param httpMethod HTTP 请求方法
     * @throws IOException 连接初始化失败时抛出
     */
    @Override
    protected void prepareConnection(@NonNull HttpURLConnection connection, @NonNull String httpMethod) throws IOException {
        super.prepareConnection(connection, httpMethod);
        if (connection instanceof HttpsURLConnection httpsConnection) {
            SSLSocketFactory socketFactory = getTrustAllSocketFactory();
            if (socketFactory != null) {
                httpsConnection.setSSLSocketFactory(socketFactory);
            }
        }
    }

    /**
     * 创建信任所有证书的 {@link SSLSocketFactory}。
     *
     * @return 信任所有证书的 Socket 工厂；创建失败时返回 {@code null} 并记录错误日志
     */
    private static SSLSocketFactory createTrustAllSocketFactory() {
        TrustManager[] trustAllManagers = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // 忽略客户端证书校验
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // 忽略服务端证书校验
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(new KeyManager[0], trustAllManagers, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (GeneralSecurityException e) {
            log.error("[离线下载]初始化信任所有证书的 SSLContext 失败，将回退到 JDK 默认证书校验", e);
            return null;
        }
    }
}
