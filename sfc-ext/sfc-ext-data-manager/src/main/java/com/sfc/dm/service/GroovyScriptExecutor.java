package com.sfc.dm.service;

import com.sfc.dm.constant.InvalidDataError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Groovy 脚本执行器，支持超时中断。
 * <p>编译时通过 {@code @ThreadInterrupt} AST 变换在脚本字节码中插入中断检查点，
 * 运行时通过工作线程 + 定时 {@link Thread#interrupt()} 实现硬超时中断。</p>
 * <p>实现 {@link AutoCloseable}，配合 try-with-resources 自动回收线程池。</p>
 */
public class GroovyScriptExecutor implements AutoCloseable {

    private static final ThreadFactory DAEMON_FACTORY = r -> {
        Thread t = new Thread(r, "groovy-filter-worker");
        t.setDaemon(true);
        return t;
    };

    private final Script compiled;
    private final ExecutorService worker;
    private final ScheduledExecutorService interrupter;

    /**
     * 编译 Groovy 脚本。
     *
     * @param script Groovy 脚本代码
     * @throws JsonException 脚本编译失败时抛出
     */
    public GroovyScriptExecutor(String script) {
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(
                new ASTTransformationCustomizer(ThreadInterrupt.class)
        );
        try {
            this.compiled = new GroovyShell(config).parse(script);
        } catch (CompilationFailedException e) {
            throw new JsonException(InvalidDataError.GROOVY_COMPILE_FAIL, e.getMessage());
        }
        this.worker = Executors.newSingleThreadExecutor(DAEMON_FACTORY);
        this.interrupter = Executors.newScheduledThreadPool(1, DAEMON_FACTORY);
    }

    /**
     * 在工作线程中执行已编译的脚本，超时后自动中断。
     *
     * @param binding       脚本变量绑定
     * @param timeoutMillis 超时时间（毫秒）
     * @return 脚本执行结果
     * @throws JsonException 脚本执行超时或异常时抛出
     */
    public Object run(Binding binding, long timeoutMillis) {
        Future<Object> future = worker.submit(() -> {
            Thread workerThread = Thread.currentThread();
            ScheduledFuture<?> interruptHandle = interrupter.schedule(
                    workerThread::interrupt,
                    timeoutMillis, TimeUnit.MILLISECONDS
            );
            try {
                compiled.setBinding(binding);
                return compiled.run();
            } finally {
                interruptHandle.cancel(false);
            }
        });

        try {
            return future.get(timeoutMillis + 1000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException
                    || (cause.getCause() instanceof InterruptedException)) {
                throw new JsonException(InvalidDataError.GROOVY_TIMEOUT);
            }
            throw new JsonException(InvalidDataError.GROOVY_EXECUTE_ERROR, cause.getMessage());
        } catch (CancellationException | TimeoutException e) {
            throw new JsonException(InvalidDataError.GROOVY_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JsonException(InvalidDataError.GROOVY_TIMEOUT);
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
        interrupter.shutdownNow();
    }
}
