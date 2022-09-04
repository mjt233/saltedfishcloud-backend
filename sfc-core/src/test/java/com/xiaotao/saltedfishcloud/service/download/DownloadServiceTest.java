package com.xiaotao.saltedfishcloud.service.download;

import com.xiaotao.saltedfishcloud.service.async.context.TaskContext;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DownloadServiceTest {
    @Resource
    private DownloadService downloadService;
    @Autowired
    private TaskManager taskManager;
    @Resource
    private TaskContextFactory factory;
    @Resource
    private DownloadTaskBuilderFactory builderFactory;

    @Test
    public void testInterrupt() throws InterruptedException {
        String url = "https://bigota.d.miui.com/V11.0.5.0.PCACNXM/miui_MI6_V11.0.5.0.PCACNXM_996ffd2660_9.0.zip";
        AsyncDownloadTaskImpl task = builderFactory.getBuilder().setUrl(url).build();
        TaskContext<AsyncDownloadTaskImpl> context = factory.createContextFromAsyncTask(task);
        taskManager.submit(context);
        downloadService.interrupt(context.getId());
        Thread.sleep(500);
        assertTrue(task.isInterrupted());
    }
}
