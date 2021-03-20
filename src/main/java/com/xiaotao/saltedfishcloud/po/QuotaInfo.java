package com.xiaotao.saltedfishcloud.po;

import lombok.Data;

/**
 * 用户配额使用情况
 */
@Data
public class QuotaInfo {
    private long used;
    private long quota;

    public long getQuota() {
        return quota*1024*1024*1024;
    }

    /**
     * 容量是否溢出
     * @return 溢出返回true
     */
    boolean isOverflow() {
        return getQuota() <= used;
    }

    /**
     * 测试能否追加size字节大小的容量而不溢出
     * @param size 要追加的大小
     * @return 溢出返回false
     */
    boolean testAppend(long size) {
        return getQuota() <= (used + size);
    }
}
