package com.sfc.task.test;

import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.Random;

@SpringBootTest(classes = RPCTest.class)
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@Slf4j
public class TaskQueueTest {
    @Autowired
    private AsyncTaskManager asyncTaskManager;

    @Test
    public void testSubmitTask() throws InterruptedException, IOException {
        log.debug("发布10个任务");
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            int timeout = random.nextInt(2000);
            asyncTaskManager.submitAsyncTask(AsyncTaskRecord.builder()
                    .cpuOverhead(10)
                    .taskType("test")
                    .params(timeout + "")
                    .name("延迟执行" + timeout + "ms")
                    .build());
        }
        Thread.sleep(5000);
    }
}
