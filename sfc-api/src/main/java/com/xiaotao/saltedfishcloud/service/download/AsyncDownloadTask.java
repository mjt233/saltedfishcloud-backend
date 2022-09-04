package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.AsyncTackCallback;
import com.xiaotao.saltedfishcloud.service.async.task.AsyncTask;

public interface AsyncDownloadTask extends AsyncTask<String, DownloadTaskStatus> {
    boolean isInterrupted();

    /**
     * 当成功建立连接，准备开始正式下载响应体时执行的回调，此时任务已获取完成文件名与大小
     * @param callback 执行回调
     */
    void onReady(AsyncTackCallback callback);

    /**
     * 下载进度发生变化时触发
     * @param callback 回调
     */
    void onProgressCallback(AsyncTackCallback callback);
}
