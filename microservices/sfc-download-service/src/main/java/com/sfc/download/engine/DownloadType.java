package com.sfc.download.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 下载类型。
 *
 * <p>本服务把「下载来源的协议」抽象为下载类型，每种类型由一个 {@link DownloadEngine} 承载实现：
 * 新增一种下载方式时，只需增加本枚举的成员并实现对应的引擎，任务处理器与业务接口均无需改动。</p>
 *
 * <p><b>序列化约定：</b>对外一律使用枚举名（见 {@link #getCode()}）作为字符串值，保证前端契约与
 * 落库字段稳定；反序列化大小写不敏感，见 {@link #fromCode(String)}。</p>
 *
 * <p><b>必填约定：</b>创建任务时必须显式指定类型，服务端不会在提交或执行阶段自动推导、也不会降级到
 * 其它类型。前端需要「按地址自动选型」时，调用类型推断接口
 * （{@link DownloadEngineRegistry#detect(String)}）取得候选类型后再提交。</p>
 */
public enum DownloadType {

    /** HTTP/HTTPS 直链下载 */
    HTTP("HTTP/HTTPS"),

    /** Git 仓库克隆 */
    GIT("Git 克隆"),

    /** 磁力链 */
    MAGNET("磁力链"),

    /** 种子文件：.torrent 地址或 Base64 种子内容 */
    TORRENT("种子"),

    /** ed2k 链接 */
    ED2K("ed2k");

    /**
     * 面向用户展示的类型名称。
     */
    private final String displayName;

    /**
     * 构造下载类型。
     *
     * @param displayName 面向用户展示的类型名称
     */
    DownloadType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取面向用户展示的类型名称。
     *
     * @return 类型展示名，永不为 {@code null}
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取序列化用的类型编码。
     *
     * <p>取值为枚举名（例如 {@code "HTTP"}），用于 JSON 序列化与数据库落库。</p>
     *
     * @return 类型编码
     */
    @JsonValue
    public String getCode() {
        return name();
    }

    /**
     * 按类型编码反序列化，大小写不敏感。
     *
     * <p>入参为 {@code null} 或空白时返回 {@code null}（随后由参数校验拒绝，
     * 提示「下载类型不能为空」）；其余无法识别的取值抛出 {@link IllegalArgumentException}
     * （由全局异常处理器转换为 400）。</p>
     *
     * @param code 类型编码，允许为 {@code null} 或空白
     * @return 对应的下载类型；编码为空时返回 {@code null}
     * @throws IllegalArgumentException 编码不是已支持的类型时抛出
     */
    @JsonCreator
    public static DownloadType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String value = code.trim();
        if (value.isEmpty()) {
            return null;
        }
        for (DownloadType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的下载类型: " + code);
    }
}
