package com.sfc.cloud.remote.client.support;

import com.sfc.cloud.remote.SfcRemoteApiException;
import com.sfc.cloud.model.json.SfcJsonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * core 错误信封处理器（RestClient 版）。错误语义与 Feign 的 {@link SfcRemoteErrorDecoder} 保持一致：
 * 响应体是 core 信封时抛 {@link SfcRemoteApiException}，否则抛出带原始响应体的 Spring 标准异常。
 */
@Slf4j
public class SfcRemoteResponseErrorHandler implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        HttpStatusCode statusCode = response.getStatusCode();
        byte[] rawBody = readBody(response);
        Charset charset = resolveCharset(response);
        String body = new String(rawBody, charset);
        SfcJsonResult<Object> envelope = SfcErrorEnvelope.parse(body);
        if (envelope != null) {
            log.debug("[sfc-remote] core 返回错误：method={}, http={}, code={}, msg={}",
                    method, statusCode.value(), envelope.getCode(), envelope.getMsg());
            throw new SfcRemoteApiException(statusCode.value(), envelope.getCode(),
                    envelope.getBusinessCode(), null, envelope.getMsg());
        }
        if (statusCode.is4xxClientError()) {
            throw HttpClientErrorException.create(statusCode, response.getStatusText(),
                    response.getHeaders(), rawBody, charset);
        }
        throw HttpServerErrorException.create(statusCode, response.getStatusText(),
                response.getHeaders(), rawBody, charset);
    }

    /**
     * 读取响应体字节。
     *
     * @param response 响应
     * @return 响应体字节
     * @throws IOException 读取失败
     */
    private byte[] readBody(ClientHttpResponse response) throws IOException {
        try (InputStream in = response.getBody()) {
            return in.readAllBytes();
        }
    }

    /**
     * 解析响应字符集。
     *
     * @param response 响应
     * @return 响应声明的字符集；未声明时使用 UTF-8
     */
    private Charset resolveCharset(ClientHttpResponse response) {
        HttpHeaders headers = response.getHeaders();
        MediaType contentType = headers.getContentType();
        Charset charset = (contentType == null ? null : contentType.getCharset());
        return (charset == null ? StandardCharsets.UTF_8 : charset);
    }
}
