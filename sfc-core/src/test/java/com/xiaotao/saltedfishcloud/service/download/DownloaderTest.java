package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactoryImpl;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManagerImpl;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Slf4j
@SpringBootTest
class DownloaderTest {
    @Autowired
    private TaskContextFactory factory;

    @Autowired
    private DownloadTaskBuilderFactory builderFactory;

    @Test
    public void testHttpFilename() throws InterruptedException {
        String url = "http://127.0.0.1:8080/api/resource/0/fileContentByFDC/eyJhbGciOiJIUzI1NiJ9.eyJkYXRhIjoie1wibmFtZVwiOlwi5oql5ZCN6KGoLnBkZlwiLFwibWQ1XCI6XCJlYWQ0ZTY1NTUwZjQ5NzBmZTg4OTgwMTFiNzBiZTAwMlwiLFwic2l6ZVwiOjAsXCJ1aWRcIjowLFwiZGlyXCI6XCIvXCIsXCJzdWZmaXhcIjpcInBkZlwifSIsImlhdCI6MTYzMDkwNDQ4MX0.wCnCXgZpY3EpUrRRpTceniWPcgQCcgEjjMB-Dq8raTw/报名表233.pdf";
        var task = builderFactory.getBuilder().setUrl(url).build();
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
        Assert.assertEquals("报名表.pdf", task.getStatus().name);

    }

    @Test
    public void testCallback() throws InterruptedException {
        var url = "https://bigota.d.miui.com/V11.0.5.0.PCACNXM/miui_MI6_V11.0.5.0.PCACNXM_996ffd2660_9.0.zip";
        var t = builderFactory.getBuilder().setUrl(url).build();
        var context = factory.createContextFromAsyncTask(t);
        context.onFinish(() -> System.out.println("finish"));
        context.onSuccess(() -> System.out.println("success"));
        context.onFailed(() -> System.out.println("failed"));
        t.setTaskId(context.getId());

        factory.getManager().submit(context);
        while (!context.isFinish()) {
            Thread.sleep(1000);
        }

    }
    @Test
    public void testProxy() {
        String url = "http://192.168.5.1";
        var t = builderFactory.getBuilder().setUrl(url)
                .setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 10808)))
                .build();
        t.start();

    }

    @Test
    public void testDownload() throws InterruptedException {
        String url = "https://down.qq.com/qqweb/LinuxQQ/linuxqq_2.0.0-b2-1089_amd64.deb";
        var t = builderFactory
                .getBuilder()
                .setUrl(url)
                .setSavePath("D:\\a.exe")
                .build();

        var factory = new TaskContextFactoryImpl(new TaskManagerImpl());
        var context = factory.createContextFromAsyncTask(t);
        factory.getManager().submit(context);

        DownloadTaskStatus status = null;
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
        log.info("任务是否已被管理器移除：{}", factory.getManager().getContext(context.getId()) == null);
    }
}
