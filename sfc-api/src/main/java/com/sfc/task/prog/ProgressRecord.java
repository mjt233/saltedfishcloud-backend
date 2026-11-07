package com.sfc.task.prog;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProgressRecord {
    public final static ProgressRecord EMPTY_RECORD = new ProgressRecord(){
        @Override
        public ProgressRecord setLoaded(long loaded) {
            return this;
        }

        @Override
        public ProgressRecord setTotal(long total) {
            return this;
        }

        @Override
        public ProgressRecord setLastUpdateTime(long lastUpdateTime) {
            return this;
        }

        @Override
        public ProgressRecord setSpeed(long speed) {
            return this;
        }
    };
    /**
     * 目标完成量，-1为未知
     */
    private long loaded;

    /**
     * 已完成的量
     */
    private long total;


    /**
     * 速度的上一次记录时间（Unix时间戳 毫秒）
     */
    private long lastUpdateTime;

    /**
     * 每毫秒完成的量
     */
    private long speed;

    /**
     * 追加已处理的量
     * @param loaded  新增的已处理的量
     * @return        该实例本身
     */
    public ProgressRecord appendLoaded(long loaded) {
        this.loaded += loaded;
        return this;
    }
}
