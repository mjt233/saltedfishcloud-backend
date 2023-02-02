package com.sfc.task.test;

import com.sfc.task.AsyncTaskManager;
import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.utils.DBUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;

@SpringBootTest(classes = RPCTest.class)
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@Slf4j
public class TaskQueueTest {
    @Autowired
    private AsyncTaskManager asyncTaskManager;

    @Autowired
    private DataSource dataSource;

    public void initDB() throws SQLException {
        Connection connection = dataSource.getConnection();
        if(DBUtils.isDBEmpty(connection)) {
            connection.getMetaData().getDatabaseProductName();
            System.out.println("数据库未初始化");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("task-sql/init.sql"));
        }
    }

    @Test
    public void testSubmitTask() throws InterruptedException, IOException, SQLException {
        initDB();
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

    @Test
    public void testGetLog() throws SQLException, IOException, InterruptedException {
        initDB();
        AsyncTaskRecord record = AsyncTaskRecord.builder()
                .cpuOverhead(0)
                .taskType("test")
                .params(100 + "")
                .name("测试日志:延迟执行100ms")
                .build();
        asyncTaskManager.submitAsyncTask(record);
        Thread.sleep(200);
        Resource taskLog = asyncTaskManager.getTaskLog(record.getId(), true);
        Assertions.assertNotNull(taskLog);
        String s = StreamUtils.copyToString(taskLog.getInputStream(), StandardCharsets.UTF_8);
        System.out.println(s);
    }
}
