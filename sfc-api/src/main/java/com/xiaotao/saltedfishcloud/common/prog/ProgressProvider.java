package com.xiaotao.saltedfishcloud.common.prog;

/**
 * 可获取进度数据和更新速度数据的接口，如下载进度，加载进度，解压进度等并能获取任务的处理进度，也可由外部检测从而更新速度数据。
 */
public interface ProgressProvider {
    /**
     * 获取任务的当前处理进度数据
     * 当使用单例对象作为返回值而不是副本时，可以不实现updateSpeed，否则您必须实现updateSpeed方法以保证速度能被正常更新。
     * @return  处理进度数据
     */
    ProgressRecord getProgressRecord();

    /**
     * 更新处理速度，当速度探测器探测到一个新的速度值的时候，将会通过该方法更新进度处理的速度。
     * @see ProgressDetector
     * @param speed 检测到的速度值
     */
    default void updateSpeed(long speed) {
        getProgressRecord().setSpeed(speed).setLastUpdateTime(System.currentTimeMillis());
    }

    /**
     * 任务处理是否已停止，且不可再恢复。
     * 返回值将决定速度是否还应该被继续探测速度。
     * @return  为true时表示任务已停止，将不再更新速度。
     */
    boolean isStop();
}
