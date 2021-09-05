package com.xiaotao.saltedfishcloud.service.async.task;

import com.xiaotao.saltedfishcloud.service.async.io.MessageReader;
import com.xiaotao.saltedfishcloud.service.async.io.MessageWriter;
import com.xiaotao.saltedfishcloud.service.async.io.TaskMessageIOPair;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

@Slf4j
public abstract class AbstractAsyncTask<MT, ST> implements AsyncTask<MT, ST> {
    protected final MessageWriter<MT> outputProducer;
    protected final MessageWriter<MT> inputProducer;
    protected final MessageReader<MT> outputConsumer;
    protected final MessageReader<MT> inputConsumer;
    private long expireAt = 0;
    @Getter
    private boolean hasStart = false;
    private boolean finish = false;
    @Setter(AccessLevel.PROTECTED)
    protected boolean expire = false;

    public AbstractAsyncTask(TaskMessageIOPair<MT> input, TaskMessageIOPair<MT> output) {
        inputConsumer = input.getReader();
        inputProducer = input.getWriter();
        outputProducer = output.getWriter();
        outputConsumer = output.getReader();
    }

    /**
     * 向外部输出一条消息
     * @param msg 消息内容
     */
    protected void provideMessage(MT msg) {
        outputProducer.write(msg);
    }

    /**
     * 读取一条外部的输入消息
     * @return 外部输入的消息，若无消息则为null
     */
    protected MT readInputMessage() {
        return inputConsumer.read();
    }

    /**
     * 向任务内部输入消息
     * @param msg 要输入的消息
     */
    public void writeMessage(MT msg) {
        inputProducer.write(msg);
    }

    /**
     * 读取任务的输出消息
     * @return 任务输出消息，若为空则为null
     */
    public MT readMessage() {
        return outputConsumer.read();
    }


    /**
     * 开始执行任务
     */
    public final boolean start() {
        synchronized (this) {
            if (hasStart) {
                throw new IllegalStateException("不可重复运行");
            }
            hasStart = true;
        }
        try {
            var res = execute();
            if (res.timeout == 0) {
                setExpire(true);
            } else if (res.timeout > 0){
                this.expireAt = res.timeout * 1000 + System.currentTimeMillis();
            }
            return res.status == AsyncTaskResult.Status.SUCCESS;
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
        } finally {
            finish = true;
        }
    }

    @Override
    public boolean isExpire() {
        return expire || (expireAt > 0 && expireAt < System.currentTimeMillis());
    }

    /**
     *  任务执行体，当返回值为0时，表示任务完成立即过期，将立即被管理器移除。
     *  当返回值大于0时，表示任务执行成功，信息最大保留的时长（秒数），超时后，isExpire将为true
     *  当返回值等于0时，表示任务执行成功且永不自动过期。
     *  当返回值等于时，表示
     */
    protected abstract AsyncTaskResult execute();

    /**
     * 任务是否已完成
     * @return true - 任务已完成，false - 任务未完成
     */
    public final boolean isFinish() {
        return finish;
    }

    @Override
    public void interrupt() {
        if (finish) {
            setExpire(true);
        }
    }
}
