package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.common.prog.ProgressDetector;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.entity.po.DownloadTaskInfo;
import com.xiaotao.saltedfishcloud.service.async.context.AsyncTackCallback;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.Proxy;
import java.util.Map;
import java.util.Optional;

/**
 * @TODO 实现多线程下载
 * @TODO 下载中途出错重试继续下载，出错一定次数后再设为任务失败
 */
@Slf4j
public class AsyncDownloadTaskImpl implements AsyncDownloadTask {
    private final RestTemplate restTemplate;
    private final String url;
    private final HttpMethod method;
    private final Map<String, String> headers;
    private final DownloadTaskStatus taskInfo = new DownloadTaskStatus();
    private boolean finish = false;
    private final DownloadExtractor extractor;
    private final ProgressDetector progressDetector;
    @Getter
    @Setter
    private String taskId;
    @Getter
    private final String savePath;

    @Override
    public boolean isInterrupted() {
        return extractor.isInterrupted();
    }

    /**
     * 创建下载任务
     * @param url               文件URL
     * @param method            请求方法
     * @param headers           额外标头
     * @param savePath          在本地文件系统上的保存路径
     * @param proxy             代理配置
     * @param connectTimeout    连接超时（毫秒）
     * @param readTimeout       读取超时（毫秒）
     * @param readyCallback     就绪回调
     * @param progressDetector  进度探测器
     */
    public AsyncDownloadTaskImpl(String url,
                                 HttpMethod method,
                                 Map<String, String> headers,
                                 String savePath,
                                 Proxy proxy,
                                 int connectTimeout, int readTimeout,
                                 AsyncTackCallback readyCallback,
                                 ProgressDetector progressDetector
    ) {
        this.progressDetector = progressDetector;
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.savePath = savePath;
        extractor = new DownloadExtractor(this.savePath);
        var factory = new IgnoreSSLHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        if (proxy != null) {
            factory.setProxy(proxy);
        }
        restTemplate = new RestTemplate(factory);
        extractor.setReadyCallback(readyCallback);
    }

    @Override
    public void onReady(AsyncTackCallback callback) {
        extractor.setReadyCallback(callback);
    }

    @Override
    public void onProgressCallback(AsyncTackCallback callback) {}

    /**
     * 中断任务的下载
     */
    @Override
    public void interrupt() {
        extractor.interrupt();
    }

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
            progressDetector.addObserve(extractor, taskId);
            HttpResourceFile res = restTemplate.execute(
                    url,
                    method,
                    restTemplate.httpEntityCallback(new HttpEntity<>(headers)),
                    extractor
            );
            if (res == null) {
                taskInfo.status = isInterrupted() ? TaskStatus.CANCEL : TaskStatus.FAILED;
                return false;
            } else {
                taskInfo.status = TaskStatus.FINISH;
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            taskInfo.error = e.getMessage();
            taskInfo.status = TaskStatus.FAILED;
            return false;
        } finally {
            finish = true;
        }
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
        final ProgressRecord recordProgress = Optional.ofNullable(progressDetector.getRecord(taskId)).orElse(ProgressRecord.EMPTY_RECORD);
        final ProgressRecord realTimeProgress = extractor.getProgressRecord();
        taskInfo.total =  realTimeProgress.getTotal();
        taskInfo.loaded = realTimeProgress.getLoaded();
        taskInfo.speed = recordProgress.getSpeed();
        return taskInfo;
    }
}
