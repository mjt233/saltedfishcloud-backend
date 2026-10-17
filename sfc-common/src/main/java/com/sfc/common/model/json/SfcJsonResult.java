package com.sfc.common.model.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sfc.cloud.exception.SfcRemoteApiException;
import com.sfc.common.utils.SfcMapperHolder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;

/**
 * 统一响应信封。
 *
 * <p><b>本类由四个宿主类合并而来</b>，是 Phase 1 迁移中唯一的"多对一"收敛点：</p>
 * <ul>
 *   <li>{@code com.xiaotao.saltedfishcloud.model.json.JsonResult}（接口）</li>
 *   <li>{@code com.xiaotao.saltedfishcloud.model.json.JsonResultImpl}（Map 实现）</li>
 *   <li>{@code com.xiaotao.saltedfishcloud.model.json.AbstractJsonResult}（{@code getJsonStr}）</li>
 *   <li>{@code com.xiaotao.saltedfishcloud.model.json.EmptySuccessJsonResult}（空成功单例）</li>
 * </ul>
 *
 * <p><b>为什么可以安全合并：</b>宿主那一套对外序列化出来的 JSON 就是
 * {@code {code, businessCode, msg, data}} 四个字段（接口的 getter 决定，内部那个 Map 不参与序列化），
 * 与本类字段逐字一致，因此<b>线协议零变化</b>、前端无需改动。</p>
 *
 * <p><b>为什么保留宿主的静态工厂名：</b>全仓库有 126 处 {@code getInstance(...)}、76 处
 * {@code emptySuccess()}、247 处以本类型作为返回值。保留这些方法名可使迁移退化为纯粹的
 * 「包名 + 类名」替换，不产生任何调用点改写，从而可脚本化、可复核、零行为变更。</p>
 *
 * <p>本类同时承担此前 {@code com.sfc.cloud.remote.model.SfcJsonResult} 的职责——
 * 作为 sfc-core 响应的反序列化目标（{@link #requireSuccess()} / {@link #getDataOrThrow()}）。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SfcJsonResult<T> {

    /** 响应代码，200 表示业务成功 */
    private int code = 200;

    /** 细分业务码，默认 200 */
    private int businessCode = 200;

    /** 提示消息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /**
     * 无参构造：语义与宿主 {@code new JsonResultImpl<>()} 一致（code=200、msg="OK"、data=null）。
     *
     * <p>同时是 Jackson 反序列化的入口。</p>
     */
    public SfcJsonResult() {
        this(200, null, "OK");
    }

    /**
     * 构造成功响应（业务码默认 200）。
     *
     * @param code 响应代码
     * @param data 业务数据
     * @param msg  提示消息
     */
    public SfcJsonResult(int code, T data, String msg) {
        this(code, 200, data, msg);
    }

    /**
     * 构造完整响应。
     *
     * @param code         响应代码
     * @param businessCode 细分业务码
     * @param data         业务数据
     * @param msg          提示消息
     */
    public SfcJsonResult(int code, int businessCode, T data, String msg) {
        this.code = code;
        this.businessCode = businessCode;
        this.data = data;
        this.msg = msg;
    }

    /**
     * 构造成功响应（code=200、msg="OK"）。
     *
     * @param data 业务数据
     * @param <T>  业务数据类型
     * @return 响应信封
     */
    public static <T> SfcJsonResult<T> getInstance(T data) {
        return new SfcJsonResult<>(200, data, "OK");
    }

    /**
     * 构造响应（业务码默认 200）。
     *
     * @param code 响应代码
     * @param data 业务数据
     * @param msg  提示消息
     * @param <T>  业务数据类型
     * @return 响应信封
     */
    public static <T> SfcJsonResult<T> getInstance(int code, T data, String msg) {
        return new SfcJsonResult<>(code, data, msg);
    }

    /**
     * 构造完整响应。
     *
     * @param code         响应代码
     * @param businessCode 细分业务码
     * @param data         业务数据
     * @param msg          提示消息
     * @param <T>          业务数据类型
     * @return 响应信封
     */
    public static <T> SfcJsonResult<T> getInstance(int code, int businessCode, T data, String msg) {
        return new SfcJsonResult<>(code, businessCode, data, msg);
    }

    /**
     * 构造"空成功"响应。
     *
     * <p>消息固定为小写 {@code "ok"}，与宿主 {@code JsonResult.emptySuccess()} 完全一致。</p>
     *
     * @return 空成功响应信封
     */
    public static SfcJsonResult<Object> emptySuccess() {
        return new SfcJsonResult<>(200, 200, null, "ok");
    }

    /**
     * 创建一个用于组装 {@code data} 的有序 Map。
     *
     * <p>与宿主 {@code JsonResultImpl.getDataMap()} 等价——宿主那里它只是个便捷工厂，
     * 与信封实例本身无关。</p>
     *
     * @return 新的空 LinkedHashMap
     */
    public static LinkedHashMap<String, Object> getDataMap() {
        return new LinkedHashMap<>();
    }

    /**
     * 是否业务成功。
     *
     * @return code 为 200 时返回 {@code true}
     */
    @JsonIgnore
    public boolean isSuccess() {
        return code == 200;
    }

    /**
     * 断言业务成功，失败时抛出异常。
     *
     * @return 自身，便于链式调用
     * @throws SfcRemoteApiException 当业务不成功时抛出
     */
    public SfcJsonResult<T> requireSuccess() {
        if (!isSuccess()) {
            throw new SfcRemoteApiException(code, code, businessCode, null, msg);
        }
        return this;
    }

    /**
     * 断言业务成功并取出业务数据。
     *
     * <p>⚠️ 必须标 {@link JsonIgnore}：本类是由宿主的「接口 {@code JsonResult} + 实现
     * {@code JsonResultImpl}」合并而来的<b>具体类</b>，Jackson 会把所有 {@code getXxx()} 都当作
     * 可序列化属性。若不加此注解会同时造成两个真实故障：</p>
     * <ol>
     *   <li>响应体里多出一个 {@code "dataOrThrow"} 字段，把业务数据<b>重复序列化一遍</b>（大响应体积翻倍）；</li>
     *   <li>更严重：序列化<b>失败响应</b>时 Jackson 会调用本方法，而它内部 {@code requireSuccess()}
     *       会抛出 {@code SfcRemoteApiException}，导致 {@code HttpMessageNotWritableException}，
     *       最终客户端拿到的是空 {@code {}} 而不是错误信封。</li>
     * </ol>
     * <p>（{@link #isSuccess()} 与 {@link #getJsonStr()} 同理，均已标注。）</p>
     *
     * @return 业务数据
     * @throws SfcRemoteApiException 当业务不成功时抛出
     */
    @JsonIgnore
    public T getDataOrThrow() {
        return requireSuccess().getData();
    }

    /**
     * 将本信封序列化为 JSON 字符串。
     *
     * <p>与宿主 {@code AbstractJsonResult.getJsonStr()} 语义一致；失败时返回 {@code "{}"} 而不抛出，
     * 因为该方法的调用点（登录成功处理器、文件上传 Servlet）位于写响应的关键路径上。</p>
     *
     * @return JSON 字符串
     */
    @JsonIgnore
    public String getJsonStr() {
        try {
            return SfcMapperHolder.mapper.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 字符串形式即其 JSON 形式（与宿主 {@code JsonResultImpl.toString()} 一致）。
     *
     * @return JSON 字符串
     */
    @Override
    public String toString() {
        return getJsonStr();
    }
}
