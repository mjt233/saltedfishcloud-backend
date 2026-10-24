package com.xiaotao.saltedfishcloud.task.worker;

import com.sfc.cloud.task.worker.SfcTaskExecution;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 任务运行日志输出流。
 *
 * <p>把任务写入的字节按 UTF-8 增量解码：每遇到换行符（{@code \n}）就把当前累积的内容
 * 作为一行日志提交给 {@link SfcTaskExecution#log(String)}，由 Worker SDK 批量上报到调度面；
 * {@link #close()} 时提交尚未换行的残余内容，保证任务结束时最后一行日志不丢失。</p>
 *
 * <p>由于 UTF-8 的续字节不会与换行符 {@code 0x0A} 冲突，按字节查找换行符不会破坏多字节字符；
 * 兼容 {@code \r\n}（行尾的 {@code \r} 会被去掉）。写入方法可安全地在多线程中使用，
 * 写入顺序即日志行顺序。</p>
 */
public class TaskLogOutputStream extends OutputStream {

    /**
     * 任务执行上下文，日志行通过它上报
     */
    private final SfcTaskExecution execution;

    /**
     * 当前行尚未遇到换行符的内容（UTF-8 字节）
     */
    private final ByteArrayOutputStream pendingLine = new ByteArrayOutputStream();

    /**
     * 构造日志输出流。
     *
     * @param execution 任务执行上下文
     */
    public TaskLogOutputStream(SfcTaskExecution execution) {
        this.execution = execution;
    }

    /**
     * 写入一个字节；遇到换行符时提交当前行。
     *
     * @param b 待写入的字节
     */
    @Override
    public void write(int b) {
        synchronized (pendingLine) {
            if (b == '\n') {
                emitPendingLine();
            } else {
                pendingLine.write(b);
            }
        }
    }

    /**
     * 批量写入字节；遇到换行符时逐行提交。
     *
     * @param bytes  字节数组
     * @param offset 起始偏移
     * @param length 写入长度
     */
    @Override
    public void write(@NotNull byte[] bytes, int offset, int length) {
        synchronized (pendingLine) {
            for (int i = offset; i < offset + length; i++) {
                if (bytes[i] == '\n') {
                    emitPendingLine();
                } else {
                    pendingLine.write(bytes[i]);
                }
            }
        }
    }

    /**
     * 关闭输出流：提交尚未换行的残余内容。
     *
     * <p>本实现不使用任何底层资源，关闭后再次关闭不会产生副作用。</p>
     */
    @Override
    public void close() {
        synchronized (pendingLine) {
            if (pendingLine.size() > 0) {
                emitPendingLine();
            }
        }
    }

    /**
     * 提交当前累积的一行日志并清空缓冲，去掉行尾可能残留的 {@code \r}。
     */
    private void emitPendingLine() {
        String line = pendingLine.toString(StandardCharsets.UTF_8);
        pendingLine.reset();
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
            line = line.substring(0, line.length() - 1);
        }
        execution.log(line);
    }
}
