package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.AsyncTackCallback;
import com.xiaotao.saltedfishcloud.service.async.context.EmptyCallback;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.*;
import lombok.experimental.Accessors;
import org.springframework.http.HttpMethod;

import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;

@Accessors(chain = true)
public class DownloadTaskBuilder {
    private String url = "";
    private HttpMethod method = HttpMethod.GET;
    private String savePath = PathUtils.getTempDirectory() + "/xyy/download_tmp/" + System.currentTimeMillis();
    private Proxy proxy = null;
    @Setter
    private int connectTimeout = 10000;
    @Setter
    private int readTimeout = 60000;
    @Setter
    private AsyncTackCallback onReadyCallback;

    public static DownloadTaskBuilder create() {
        return new DownloadTaskBuilder();
    }

    public static DownloadTaskBuilder create(String url) {
        return new DownloadTaskBuilder().setUrl(url);
    }

    @Setter(AccessLevel.NONE)
    private Map<String, String> headers = new HashMap<>();

    public DownloadTaskBuilder setHeader(String k, String v) {
        headers.put(k, v);
        return this;
    }

    public DownloadTaskBuilder setRange(int begin) {
        headers.put("Range", "bytes=" + begin + "-");
        return this;
    }

    public DownloadTaskBuilder setRange(long begin, long end) {
        headers.put("Range", "bytes=" + begin + "-" + end);
        return this;
    }

    public DownloadTask build() {
        return new DownloadTask(url, method, headers, savePath,
                proxy, connectTimeout, readTimeout,
                onReadyCallback == null ? EmptyCallback.inst : onReadyCallback);
    }

    public DownloadTaskBuilder setUrl(String url) {
        this.url = url;
        return this;
    }

    public DownloadTaskBuilder setMethod(HttpMethod method) {
        this.method = method;
        return this;
    }

    public DownloadTaskBuilder setSavePath(String savePath) {
        this.savePath = savePath;
        return this;
    }

    public DownloadTaskBuilder setProxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    public DownloadTaskBuilder setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }
}
