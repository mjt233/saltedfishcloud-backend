package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.AsyncTackCallback;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.Proxy;
import java.util.Map;

/**
 * @TODO 实现多线程下载
 */
@Slf4j
public class DownloadTask implements AsyncTask<String, DownloadTaskStatus> {
    private final RestTemplate restTemplate;
    private final String url;
    private final HttpMethod method;
    private final Map<String, String> headers;
    private final DownloadTaskStatus taskInfo = new DownloadTaskStatus();
    private boolean finish = false;
    private final DownloadExtractor extractor;
    @Getter
    private final String savePath;

    public DownloadTask(String url, HttpMethod method, Map<String, String> headers, String savePath, Proxy proxy,
                        int connectTimeout, int readTimeout, AsyncTackCallback readyCallback) {
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.savePath = savePath;
        extractor = new DownloadExtractor(this.savePath);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        if (proxy != null) {
            factory.setProxy(proxy);
        }
        restTemplate = new RestTemplate(factory);
        extractor.setReadyCallback(readyCallback);
    }

    /**
     * 当成功建立连接，准备开始正式下载响应体时执行的回调，此时任务已获取完成文件名与大小
     * @param callback 执行回调
     */
    public void onReady(AsyncTackCallback callback) {
        extractor.setReadyCallback(callback);
    }

    /**
     * 下载进度发生变化时触发
     * @param callback 回调
     */
    public void onProgressCallback(AsyncTackCallback callback) {
        extractor.setProgressCallback(callback);
    }

    /**
     * @TODO 实现下载中断
     */
    @Override
    public void interrupt() { }

    @Override
    public boolean isExpire() {
        return finish;
    }

    @Override
    public void writeMessage(String msg) {

    }

    @Override
    public String readMessage() {
        return null;
    }

    @Override
    public boolean start() {
        // 初始化任务信息
        taskInfo.url = url;
        try {
            taskInfo.name = StringUtils.getURLLastName(url);
            log.debug("通过URL获取的默认文件名：" + taskInfo.name);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36");
        if(this.headers != null) {
            this.headers.forEach(headers::add);
        }

        // 开始执行下载
        taskInfo.status = TaskStatus.DOWNLOADING;
        try {
            restTemplate.execute(
                    url,
                    method,
                    restTemplate.httpEntityCallback(new HttpEntity<>(headers)),
                    extractor
            );
            taskInfo.status = TaskStatus.FINISH;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            taskInfo.error = e.getMessage();
            taskInfo.status = TaskStatus.FAILED;
            return false;
        }
        finish = true;
        return true;
    }

    @Override
    public boolean isFinish() {
        return finish;
    }

    @Override
    public DownloadTaskStatus getStatus() {
        if (extractor.getResourceName() != null) {
            taskInfo.name = extractor.getResourceName();
        }
        taskInfo.total = extractor.getTotal();
        taskInfo.loaded = extractor.getLoaded();
        taskInfo.speed = extractor.getSpeed();
        return taskInfo;
    }
}
