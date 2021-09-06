package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManagerImpl;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Slf4j
class DownloaderTest {
    private TaskContextFactory factory =  new TaskContextFactory(new TaskManagerImpl());

    @Test
    public void testHttpFilename() throws InterruptedException {
        String url = "https://disk.xiaotao2333.top:344/api/resource/0/fileContentByFDC/eyJhbGciOiJIUzI1NiJ9.eyJkYXRhIjoie1wibmFtZVwiOlwiTWluZWNyYWZ0MS4xMi4yLnppcFwiLFwibWQ1XCI6XCJlMzBjMmY4NDI1ZTkyZmQxYmYzY2M0ZWZiZWMxMjljYlwiLFwic2l6ZVwiOjAsXCJ1aWRcIjowLFwiZGlyXCI6XCIv5ri45oiP55u45YWzL01pbmVjcmFmdFwiLFwic3VmZml4XCI6XCJ6aXBcIn0iLCJpYXQiOjE2MzA4OTcwODl9.QiNpzso1l595LBaoONdjRy_-6iBL5nHic-9G7ahFyS0/Minecraftasdasdasd1.12.2.zip";
        var task = DownloadTaskBuilder.create(url).build();
        var context = factory.createContextFromAsyncTask(task);
        factory.getManager().submit(context);
        double prog = 0;
        long lastRecord = 0;
        while (!task.isFinish()) {
            var status = task.getStatus();
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
        Assert.assertEquals("Minecraft1.12.2.zip", task.getStatus().name);

    }

    @Test
    public void testCallback() {
        var url = "https://down.qq.com/qqweb/LinuxQQ/linuxqq_2.0.0-b2-1089_amd6a4.deb";
        var t = DownloadTaskBuilder.create(url).build();
        var context = factory.createContextFromAsyncTask(t);
        context.onFinish(() -> System.out.println("finish"));
        context.onSuccess(() -> System.out.println("success"));
        context.onFailed(() -> System.out.println("failed"));

        factory.getManager().submit(context);
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
    @Test
    public void testProxy() {
        String url = "http://192.168.5.1";
        var t = DownloadTaskBuilder
                .create(url)
                .setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 10808)))
                .build();
        t.start();

    }

    @Test
    public void testDownload() throws InterruptedException {
        String url = "https://down.qq.com/qqweb/LinuxQQ/linuxqq_2.0.0-b2-1089_amd64.deb";
        var t = DownloadTaskBuilder
                .create(url)
                .setSavePath("D:\\a.exe")
                .build();

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
