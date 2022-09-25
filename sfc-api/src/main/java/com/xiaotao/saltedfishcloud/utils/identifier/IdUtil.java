package com.xiaotao.saltedfishcloud.utils.identifier;

import java.util.Optional;

public class IdUtil {
    private static final SnowFlake snowFlakeInst;
    static {
        // 获取数据中心id
        String dcid = Optional.ofNullable(System.getenv("DCID")).orElse("0");

        // 获取机器id
        String mid = Optional.ofNullable(System.getenv("MID")).orElse("0");
        snowFlakeInst = new SnowFlake(Long.parseLong(dcid), Long.parseLong(mid));
    }

    public static long getId() {
        return snowFlakeInst.nextId();
    }
}
