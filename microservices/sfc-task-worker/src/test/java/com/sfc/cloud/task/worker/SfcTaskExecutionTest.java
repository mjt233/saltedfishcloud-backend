package com.sfc.cloud.task.worker;

import com.sfc.cloud.task.worker.protocol.TaskDispatchInfo;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SfcTaskExecution} 的单元测试：检查点读写与挂起/中断回调语义。
 */
class SfcTaskExecutionTest {

    /**
     * 构造一个下发的任务信息。
     *
     * @param checkpoint 下发的检查点，可为 null
     * @return 任务信息
     */
    private static TaskDispatchInfo dispatchInfo(String checkpoint) {
        return new TaskDispatchInfo(1L, "测试任务", "test-type", "{}", 1L, 100, 0, new Date(), checkpoint);
    }

    /**
     * 构造一个执行上下文（保存器行为由参数决定）。
     *
     * @param initialCheckpoint 下发的检查点
     * @param saver             检查点保存器
     * @return 执行上下文
     */
    private static SfcTaskExecution execution(String initialCheckpoint, SfcTaskExecution.CheckpointSaver saver) {
        return new SfcTaskExecution(dispatchInfo(initialCheckpoint), "127.0.0.1:9000", saver);
    }

    /**
     * 验证下发时携带的检查点会作为 {@link SfcTaskExecution#getCheckpoint()} 的初始值。
     */
    @Test
    void testCheckpointInitializedFromDispatchInfo() {
        SfcTaskExecution execution = execution("{\"phase\":\"DOWNLOADING\"}", (e, cp) -> true);

        assertEquals("{\"phase\":\"DOWNLOADING\"}", execution.getCheckpoint());
        assertFalse(execution.isDetachRequested());
        assertFalse(execution.isInterruptRequested());
    }

    /**
     * 验证无检查点时初始值为 null。
     */
    @Test
    void testCheckpointNullWhenAbsent() {
        assertNull(execution(null, (e, cp) -> true).getCheckpoint());
    }

    /**
     * 验证保存检查点会同步委派给保存器，并立即刷新 {@link SfcTaskExecution#getCheckpoint()}。
     */
    @Test
    void testSaveCheckpointDelegatesAndUpdatesLatestValue() {
        AtomicReference<String> saved = new AtomicReference<>();
        SfcTaskExecution execution = execution("old", (e, cp) -> {
            saved.set(cp);
            return true;
        });

        boolean result = execution.saveCheckpoint("new-checkpoint");

        assertTrue(result);
        assertEquals("new-checkpoint", saved.get());
        assertEquals("new-checkpoint", execution.getCheckpoint());
        assertNull(execution.getPendingCheckpoint());
    }

    /**
     * 验证保存失败时返回 false、保留最新值并记为待重发，不向调用方抛异常。
     */
    @Test
    void testSaveCheckpointFailureKeepsLatestValueAndMarksPending() {
        SfcTaskExecution execution = execution("old", (e, cp) -> false);

        boolean result = execution.saveCheckpoint("new-checkpoint");

        assertFalse(result);
        assertEquals("new-checkpoint", execution.getCheckpoint());
        assertEquals("new-checkpoint", execution.getPendingCheckpoint());
    }

    /**
     * 验证保存器抛出异常时同样不向任务实现抛出，只记为待重发。
     */
    @Test
    void testSaveCheckpointSaverThrowsSwallowed() {
        SfcTaskExecution execution = execution(null, (e, cp) -> {
            throw new IllegalStateException("模拟保存器异常");
        });

        assertFalse(execution.saveCheckpoint("cp"));
        assertEquals("cp", execution.getPendingCheckpoint());
    }

    /**
     * 验证空白检查点不会触发保存。
     */
    @Test
    void testSaveCheckpointBlankIgnored() {
        AtomicInteger calls = new AtomicInteger();
        SfcTaskExecution execution = execution("init", (e, cp) -> {
            calls.incrementAndGet();
            return true;
        });

        assertFalse(execution.saveCheckpoint("  "));
        assertEquals(0, calls.get());
        assertEquals("init", execution.getCheckpoint());
    }

    /**
     * 验证任务已结束后不再保存检查点。
     */
    @Test
    void testSaveCheckpointAfterFinishedIgnored() {
        AtomicInteger calls = new AtomicInteger();
        SfcTaskExecution execution = execution(null, (e, cp) -> {
            calls.incrementAndGet();
            return true;
        });
        assertTrue(execution.markFinished());

        assertFalse(execution.saveCheckpoint("cp"));
        assertEquals(0, calls.get());
    }

    /**
     * 验证挂起指令会触发已注册的挂起回调，且重复下发只触发一次。
     */
    @Test
    void testDetachCallbackRunsOnce() {
        SfcTaskExecution execution = execution(null, (e, cp) -> true);
        AtomicInteger calls = new AtomicInteger();
        execution.onDetach(calls::incrementAndGet);

        execution.requestDetach();
        execution.requestDetach();

        assertTrue(execution.isDetachRequested());
        assertEquals(1, calls.get());
    }

    /**
     * 验证挂起指令到达后注册的回调会立即执行。
     */
    @Test
    void testOnDetachAfterRequestedRunsImmediately() {
        SfcTaskExecution execution = execution(null, (e, cp) -> true);
        execution.requestDetach();
        AtomicInteger calls = new AtomicInteger();

        execution.onDetach(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }

    /**
     * 验证挂起回调与中断回调相互独立。
     */
    @Test
    void testDetachAndInterruptCallbacksIndependent() {
        SfcTaskExecution execution = execution(null, (e, cp) -> true);
        AtomicInteger detachCalls = new AtomicInteger();
        AtomicInteger interruptCalls = new AtomicInteger();
        execution.onDetach(detachCalls::incrementAndGet);
        execution.onInterrupt(interruptCalls::incrementAndGet);

        execution.requestDetach();

        assertTrue(execution.isDetachRequested());
        assertFalse(execution.isInterruptRequested());
        assertEquals(1, detachCalls.get());
        assertEquals(0, interruptCalls.get());

        execution.requestInterrupt();

        assertTrue(execution.isInterruptRequested());
        assertEquals(1, detachCalls.get());
        assertEquals(1, interruptCalls.get());
    }

    /**
     * 验证单个回调抛出异常不影响其余回调执行。
     */
    @Test
    void testCallbackFailureDoesNotAffectOthers() {
        SfcTaskExecution execution = execution(null, (e, cp) -> true);
        AtomicInteger calls = new AtomicInteger();
        execution.onInterrupt(() -> {
            throw new IllegalStateException("模拟回调异常");
        });
        execution.onInterrupt(calls::incrementAndGet);

        execution.requestInterrupt();

        assertEquals(1, calls.get());
    }

    /**
     * 验证多线程并发下发挂起指令时回调只执行一次（CAS 互斥）。
     *
     * @throws InterruptedException 等待并发任务结束时被中断
     */
    @Test
    void testConcurrentDetachCallbackRunsOnce() throws InterruptedException {
        SfcTaskExecution execution = execution(null, (e, cp) -> true);
        AtomicInteger calls = new AtomicInteger();
        execution.onDetach(calls::incrementAndGet);
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    execution.requestDetach();
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertTrue(execution.isDetachRequested());
        assertEquals(1, calls.get());
    }
}
