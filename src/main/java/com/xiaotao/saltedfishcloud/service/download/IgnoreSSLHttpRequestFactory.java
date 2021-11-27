package com.xiaotao.saltedfishcloud.service.download;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * 忽略SSL证书验证的HTTP请求工厂
 */
public class IgnoreSSLHttpRequestFactory extends SimpleClientHttpRequestFactory {
    private SSLSocketFactory sslSocketFactory;

    private SSLSocketFactory getSSLSocketFactory() {
        if (sslSocketFactory != null) return sslSocketFactory;
        TrustManager[] ignoreTrustManagers = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, ignoreTrustManagers, new SecureRandom());
            this.sslSocketFactory = sslContext.getSocketFactory();
            return sslSocketFactory;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        super.prepareConnection(connection, httpMethod);
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(getSSLSocketFactory());
        }
    }
}
