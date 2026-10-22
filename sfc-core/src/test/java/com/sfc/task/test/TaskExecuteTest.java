package com.sfc.task.test;

import com.sfc.task.DefaultAsyncTaskExecutor;
import com.sfc.task.TestAsyncTaskFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地任务执行器测试。
 */
public class TaskExecuteTest {

    /**
     * 验证任务工厂注册与任务类型能力上报。
     */
    @Test
    public void testRegisterFactory() {
        DefaultAsyncTaskExecutor executor = new DefaultAsyncTaskExecutor();
        executor.registerFactory(new TestAsyncTaskFactory());
        assertTrue(executor.getRegisteredTaskTypes().contains("test"));
        assertThrows(IllegalArgumentException.class, () -> executor.registerFactory(new TestAsyncTaskFactory()));
    }
}
