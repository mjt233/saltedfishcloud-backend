package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import com.xiaotao.saltedfishcloud.entity.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.service.async.context.AsyncTackCallback;
import com.xiaotao.saltedfishcloud.service.async.context.EmptyCallback;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.http.HttpMethod;

import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Accessors(chain = true)
@Data
public class DownloadTaskBuilder {
    private String url = "";
    private HttpMethod method = HttpMethod.GET;
    private String savePath = PathUtils.getTempDirectory() + "/xyy/download_tmp/" + System.currentTimeMillis();
    private Proxy proxy = null;
    private int connectTimeout = 10000;
    private int readTimeout = 60000;
    private AsyncTackCallback onReadyCallback;
    private ProgressDetector detector;

    public static DownloadTaskBuilder create(String url, ProgressDetector detector) {
        return new DownloadTaskBuilder(detector).setUrl(url);
    }

    public DownloadTaskBuilder(ProgressDetector detector) {
        this.detector = detector;
    }

    @Setter(AccessLevel.NONE)
    private Map<String, String> headers = new HashMap<>();

    public DownloadTaskBuilder setHeader(String k, String v) {
        headers.put(k, v);
        return this;
    }

    public AsyncDownloadTaskImpl build() {
        return new AsyncDownloadTaskImpl(
                url,
                method,
                headers,
                savePath,
                proxy,
                connectTimeout,
                readTimeout,
                Optional.ofNullable(onReadyCallback).orElse(EmptyCallback.inst),
                detector);
    }


    public DownloadTaskBuilder setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }
}
