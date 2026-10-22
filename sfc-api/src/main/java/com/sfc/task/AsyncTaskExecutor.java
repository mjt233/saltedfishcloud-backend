package com.sfc.task;

import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * 任务执行器。
 * <p>执行器只负责"执行被认领到的任务"，不负责从队列中抓取任务——任务认领由任务管理器的调度体系
 * 按任务类型路由完成。</p>
 */
public interface AsyncTaskExecutor {
    /**
     * 获取系统当前负载
     */
    int getCurrentLoad();

    /**
     * 开始接受任务
     */
    void start();

    /**
     * 停止全部任务并停止接受任务
     * @return 被中断的任务id
     */
    Collection<Long> stop();

    /**
     * 获取正在执行的任务id
     */
    Collection<Long> getRunningTask();

    /**
     * 放弃一个任务的执行。中断任务执行，不更新状态和触发正常事件。<br>
     * 用于故障转移，任务执行期间节点掉线后，任务被其他节点接管。
     * @param taskId    要放弃的任务id
     */
    void giveUp(Long taskId);

    /**
     * 是否处于运行中
     */
    boolean isRunning();

    /**
     * 获取系统最大负载
     */
    int getMaxLoad();

    /**
     * 中断任务
     * @param taskId 任务id
     */
    boolean interrupt(Long taskId);

    /**
     * 设置系统最大负载
     */
    void setMaxLoad(int maxLoad);

    /**
     * 获取任务的日志
     * @param taskId     任务id
     * @return 任务最新的运行日志，若获取不到则为null
     */
    String getLog(Long taskId) throws IOException;

    /**
     * 获取运行中的任务实例
     * @param taskId 任务id
     */
    AsyncTask getTask(Long taskId);

    /**
     * 注册一个任务工厂
     * @param factory 该类型对应的任务工厂
     */
    void registerFactory(AsyncTaskFactory factory);

    /**
     * 获取任务进度
     */
    ProgressRecord getProgress(Long taskId) throws IOException;

    /**
     * 获取本执行器已注册的任务类型集合。
     * <p>执行器只会认领该集合中的任务类型。</p>
     *
     * @return 已注册的任务类型集合
     */
    Collection<String> getRegisteredTaskTypes();

    /**
     * 添加任务开始执行的监听器
     */
    void addTaskStartListener(Consumer<AsyncTaskRecord> listener);

    /**
     * 添加任务执行失败的监听器
     */
    void addTaskFailedListener(Consumer<AsyncTaskRecord> listener);

    /**
     * 添加任务执行完成的监听器
     */
    void addTaskFinishListener(Consumer<AsyncTaskRecord> listener);

    /**
     * 添加一个任务退出监听器。无论任务因何种原因退出或结束时会触发。
     */
    void addTaskExitListener(Consumer<AsyncTaskRecord> listener);

    /**
     * 直接执行任务，不加入队列调度
     * @param record 任务记录
     */
    void execute(AsyncTaskRecord record);

    /**
     * 执行一个已被本执行者认领的任务。
     * <p>与 {@link #execute(AsyncTaskRecord)} 的区别：认领到的任务会计入系统负载，
     * 而直接执行的任务不受系统负载约束。</p>
     *
     * @param record 已被认领的任务记录
     */
    void executeClaimed(AsyncTaskRecord record);
}
