package com.sfc.cloud.data.id;

/**
 * twitter的snowflake算法 -- java实现
 * 来自：https://github.com/beyondfengyu/SnowFlake
 * @author beyond
 * @date 2016/11/26
 *
 * <p>本类是<b>纯算法实现</b>，不依赖任何第三方库，也不依赖宿主语义，
 * 因此可被宿主与微服务两侧共同复用。</p>
 *
 * <p>在共享数据访问基座中的使用方式：JPA 实体主键由
 * {@link SfcSnowFlakeIdGeneratorImpl}（配合注解 {@link SfcSnowFlakeIdGenerator}）产生，
 * 该生成器最终委托到 {@link SfcIdUtil#getId()}，而 {@link SfcIdUtil} 持有本类的单例。</p>
 */
public class SfcSnowFlake {

    /**
     * 起始的时间戳
     */
    private final static long START_STMP = 1480166465631L;

    /**
     * 每一部分占用的位数
     */
    private final static long SEQUENCE_BIT = 12; //序列号占用的位数
    private final static long MACHINE_BIT = 5;   //机器标识占用的位数
    private final static long DATACENTER_BIT = 5;//数据中心占用的位数

    /**
     * 每一部分的最大值
     */
    private final static long MAX_DATACENTER_NUM = -1L ^ (-1L << DATACENTER_BIT);
    private final static long MAX_MACHINE_NUM = -1L ^ (-1L << MACHINE_BIT);
    private final static long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BIT);

    /**
     * 每一部分向左的位移
     */
    private final static long MACHINE_LEFT = SEQUENCE_BIT;
    private final static long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private final static long TIMESTMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    /**
     * 数据中心id
     */
    private long datacenterId;  //数据中心
    /**
     * 机器标识id
     */
    private long machineId;     //机器标识
    /**
     * 当前毫秒内的序列号
     */
    private long sequence = 0L; //序列号
    /**
     * 上一次生成id时使用的时间戳
     */
    private long lastStmp = -1L;//上一次时间戳

    /**
     * 构造一个雪花id生成器。
     *
     * @param datacenterId 数据中心id，取值范围 [0, {@link #MAX_DATACENTER_NUM}]
     * @param machineId    机器标识id，取值范围 [0, {@link #MAX_MACHINE_NUM}]
     * @throws IllegalArgumentException 当数据中心id或机器标识id超出取值范围时抛出
     */
    public SfcSnowFlake(long datacenterId, long machineId) {
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId can't be greater than MAX_DATACENTER_NUM or less than 0");
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    /**
     * 产生下一个ID
     *
     * @return 全局唯一且趋势递增的雪花id
     * @throws RuntimeException 当检测到系统时钟回拨时抛出
     */
    public synchronized long nextId() {
        long currStmp = getCurrentTimestamp();
        if (currStmp < lastStmp) {
            throw new RuntimeException("Clock moved backwards.  Refusing to generate id");
        }

        if (currStmp == lastStmp) {
            //相同毫秒内，序列号自增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            //同一毫秒的序列数已经达到最大
            if (sequence == 0L) {
                currStmp = getNextMill();
            }
        } else {
            //不同毫秒内，序列号置为0
            sequence = 0L;
        }

        lastStmp = currStmp;

        return (currStmp - START_STMP) << TIMESTMP_LEFT //时间戳部分
                | datacenterId << DATACENTER_LEFT       //数据中心部分
                | machineId << MACHINE_LEFT             //机器标识部分
                | sequence;                             //序列号部分
    }

    /**
     * 阻塞自旋直到取得下一个毫秒的时间戳。
     *
     * @return 大于 {@link #lastStmp} 的时间戳
     */
    private long getNextMill() {
        long mill = getCurrentTimestamp();
        while (mill <= lastStmp) {
            mill = getCurrentTimestamp();
        }
        return mill;
    }

    /**
     * 获取当前系统时间戳。
     *
     * @return 当前时间的毫秒时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
}
