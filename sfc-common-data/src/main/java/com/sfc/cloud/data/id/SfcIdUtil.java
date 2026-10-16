package com.sfc.cloud.data.id;

import java.util.Optional;
import java.util.UUID;

/**
 * 共享数据访问基座的id工具类。
 *
 * <p>迁移自宿主的 {@code com.xiaotao.saltedfishcloud.utils.identifier.IdUtil}，
 * 去除了对宿主 {@code SecureUtils} 的依赖（{@link #getUUID()} 改为等价的纯 JDK 实现），
 * 因此本类不引入任何宿主语义。</p>
 *
 * <p><b>集群配置</b>：雪花算法的数据中心id与机器id分别读取环境变量
 * {@code DCID} 与 {@code MID}，未设置时均缺省为 {@code "0"}。</p>
 */
public class SfcIdUtil {

    /**
     * 雪花id生成器单例。
     *
     * <p>在类初始化时依据环境变量 {@code DCID} / {@code MID} 构建，
     * 因此这两个环境变量必须在类首次被加载之前设置完毕。</p>
     */
    private static final SfcSnowFlake snowFlakeInst;

    static {
        // 获取数据中心id
        String dcid = Optional.ofNullable(System.getenv("DCID")).orElse("0");

        // 获取机器id
        String mid = Optional.ofNullable(System.getenv("MID")).orElse("0");
        snowFlakeInst = new SfcSnowFlake(Long.parseLong(dcid), Long.parseLong(mid));
    }

    /**
     * 生成一个全局唯一的雪花id。
     *
     * @return 雪花id
     */
    public static long getId() {
        return snowFlakeInst.nextId();
    }

    /**
     * 生成一个随机UUID字符串。
     *
     * <p><b>输出格式</b>：与宿主 {@code SecureUtils.getUUID()} 完全一致——
     * 基于 {@link UUID#randomUUID()} 生成，随后删除全部 {@code "-"} 分隔符，
     * 即 <b>32 位小写十六进制字符、不含连字符</b>（例如
     * {@code 6f5902ac237024bdd0c176cb93063dc4}）。</p>
     *
     * @return 32位无连字符的小写十六进制UUID字符串
     */
    public static String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
