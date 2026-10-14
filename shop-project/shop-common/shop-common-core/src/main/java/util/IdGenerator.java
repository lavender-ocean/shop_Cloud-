package util;



//未使用，仅雪花原理参考
/**
 * 雪花算法 ID 生成器
 * 64位long结构：1bit符号位(固定0) + 41bit时间戳 + 5bit数据中心ID + 5bit机器ID + 12bit序列号
 * 特点：本地生成全局唯一ID，趋势递增；需要保证workerId+datacenterId集群内唯一
 * 风险点：服务器时钟回拨会造成ID重复，本实现做了基础的时钟回拨防护
 */
public class IdGenerator {
    // 当前机器ID
    private final long workerId;
    // 数据中心/机房ID
    private final long datacenterId;
    // 同一毫秒内的序列号，0~4095
    private long sequence = 0L;
    // 上一次生成ID使用的时间戳（毫秒）
    private long lastTimestamp = -1L;

    // 自定义纪元：2024-01-01 00:00:00，减少时间戳占用数值，41bit可使用约69年
    private static final long EPOCH = 1704067200000L;

    // 机器ID占用bit位数
    private static final long WORKER_ID_BITS = 5L;
    // 数据中心ID占用bit位数
    private static final long DATACENTER_ID_BITS = 5L;
    // 序列号占用bit位数
    private static final long SEQUENCE_BITS = 12L;

    // 机器ID最大值：2^5 -1 = 31
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    // 数据中心ID最大值：2^5 -1 =31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    // 机器ID左移位数：序列号12位
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    // 数据中心ID左移位数：序列号12 + 机器5 =17位
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    // 时间戳左移位数：序列号12 +机器5 +机房5 =22位
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    // 序列号掩码，二进制12个1，用于&运算截断，保证序列号不超过12bit 最大值4095
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /**
     * 构造雪花ID生成器
     * @param datacenterId 数据中心ID [0,31]
     * @param workerId 机器实例ID [0,31]
     */
    public IdGenerator(long datacenterId, long workerId) {
        // 校验机器ID范围
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 超出范围，取值0~31");
        }
        // 校验数据中心ID范围
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId 超出范围，取值0~31");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成唯一ID，synchronized保证单实例多线程安全
     * @return 雪花long类型ID
     */
    public synchronized long nextId() {
        // 获取当前系统毫秒时间戳
        long timestamp = System.currentTimeMillis();

        // ========== 时钟回拨处理核心逻辑 ==========
        // 当前时间 < 上次生成ID时间：服务器时间回退（NTP同步导致），有ID重复风险
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            // 回拨小于等于5ms：短暂等待，等系统时间追上上次时间戳
            if (offset <= 5) {
                try {
                    wait(offset);
                    // 等待结束后重新获取时间
                    timestamp = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                // 回拨过大，直接抛出异常，宁可失败，绝不产生重复ID
                throw new IllegalStateException("时钟回拨过大，拒绝生成 ID："
                        + offset + "ms");
            }
        }

        // 同一毫秒，继续递增序列号
        if (timestamp == lastTimestamp) {
            // 序列号+1，与掩码做与运算，超过4095自动归零
            sequence = (sequence + 1) & SEQUENCE_MASK;
            // 序列号归零：说明当前毫秒序列号耗尽，自旋等待到下一毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 新的毫秒，序列号重置为0
            sequence = 0L;
        }

        // 更新上一次的时间戳
        lastTimestamp = timestamp;

        // 位运算拼接64位ID：时间戳 | 数据中心 | 机器ID | 序列号
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 自旋阻塞，直到获取大于lastTimestamp的时间戳
     * @param lastTimestamp 上一次生成ID的时间戳
     * @return 新的时间戳
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        // 循环直到时间戳超过上次时间
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
