package com.sfc.task;

import com.sfc.task.model.AsyncTaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AsyncTaskFactoryRegistry} 的单元测试。
 */
public class AsyncTaskFactoryRegistryTest {

    /**
     * 验证注册成功后可查询到任务类型与工厂。
     */
    @Test
    public void testRegisterSuccess() {
        AsyncTaskFactoryRegistry registry = new AsyncTaskFactoryRegistry();
        TestAsyncTaskFactory factory = new TestAsyncTaskFactory();

        registry.register(factory);

        assertEquals(1, registry.getFactories().size());
        assertTrue(registry.getTaskTypes().contains("test"));
        assertEquals(factory, registry.getFactories().getFirst());
    }

    /**
     * 验证同一工厂实例重复注册是幂等的。
     */
    @Test
    public void testRegisterSameInstanceIdempotent() {
        AsyncTaskFactoryRegistry registry = new AsyncTaskFactoryRegistry();
        TestAsyncTaskFactory factory = new TestAsyncTaskFactory();

        registry.register(factory);
        registry.register(factory);

        assertEquals(1, registry.getFactories().size());
        assertEquals(1, registry.getTaskTypes().size());
    }

    /**
     * 验证不同工厂注册相同任务类型时抛出异常。
     */
    @Test
    public void testRegisterDuplicateTypeThrows() {
        AsyncTaskFactoryRegistry registry = new AsyncTaskFactoryRegistry();
        registry.register(new TestAsyncTaskFactory());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registry.register(new TestAsyncTaskFactory()));
        assertTrue(exception.getMessage().contains("任务类型 test 的任务工厂已被注册"));
    }

    /**
     * 验证任务类型为空时抛出异常。
     */
    @Test
    public void testRegisterEmptyTaskTypeThrows() {
        AsyncTaskFactoryRegistry registry = new AsyncTaskFactoryRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.register(new EmptyTypeTaskFactory()));
    }

    /**
     * 任务类型为空的任务工厂。
     */
    private static class EmptyTypeTaskFactory implements AsyncTaskFactory {

        @Override
        public AsyncTask createTask(String params, AsyncTaskRecord asyncTaskRecord) {
            return null;
        }

        @Override
        public String getTaskType() {
            return " ";
        }
    }
}
