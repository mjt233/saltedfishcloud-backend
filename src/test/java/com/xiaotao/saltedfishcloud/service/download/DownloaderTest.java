package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManagerImpl;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;


@Slf4j
class DownloaderTest {

    @Test
    public void testDownload() throws InterruptedException {
        String url = "https://down.qq.com/qqweb/LinuxQQ/linuxqq_2.0.0-b2-1089_amd64.deb";
        var t = new DownloadTask(url, HttpMethod.GET, null, "D:\\a.exe", null);
        var factory = new TaskContextFactory(new TaskManagerImpl());
        var context = factory.createContextFromAsyncTask(t);
        factory.getManager().submit(context);

        DownloadTaskStatus status = null;
        int speed = 0;
        long lastRecord = 0;
        double prog = -1;
        while (!context.isFinish()) {
            status = t.getStatus();
            Thread.sleep(500);
            if (status.total != 0) {
                prog = ((double)status.loaded / status.total) * 100;
            }
            log.info("文件大小：{} 已下载：{} 进度：{}% 速度：{}kiB/s",
                    status.total,
                    status.loaded,
                    String.format("%.2f", prog),
                    ((status.loaded - lastRecord)*2)/1024
            );
            lastRecord = status.loaded;
        }
        if (status != null && status.error != null) {
            log.error("下载错误：{}", status.error);
        } else {
            log.info("下载完成");
        }
        log.info("任务是否已被管理器移除：{}", factory.getManager().getTask(context.getId()) == null);
    }
}
