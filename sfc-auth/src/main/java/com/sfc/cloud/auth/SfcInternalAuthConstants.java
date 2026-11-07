package com.sfc.cloud.auth;

/**
 * 内部接口（服务间调用）契约常量：请求头名、路径前缀、JWT 约定与统一拒绝响应。
 *
 * <p>本类是全仓库该契约的<b>唯一来源</b>：core 侧过滤器、网关前缀拒绝、远程客户端拦截器
 * 都从这里取值，不要再写字面量。</p>
 */
public interface SfcInternalAuthConstants {

    interface Header {

        /** 服务间调用凭证请求头名：值为每次调用新签发的 HS256 JWT（<b>不是</b>密钥本身） */
        String INTERNAL_TOKEN = "X-Sfc-Internal-Token";
    }

    interface Env {
        /**
         * 服务间调用凭证的共享密钥环境变量名
         */
        String INTERNAL_KEY = "SFC_INTERNAL_KEY";
    }


    /** 内部接口路径前缀：该前缀下的接口只允许微服务间调用 */
    String PATH_PREFIX = "/api/internal";

    /** JWT 的 {@code sub} 固定取值，用于把内部调用凭证与用户会话凭证区分开 */
    String JWT_SUBJECT = "sfc-internal";

    /**
     * 密钥最小长度（字符）。HS256 要求密钥至少 256 bit，jjwt 会用 {@code WeakKeyException} 拒绝更短的密钥；
     * 本 SDK 在启动期就给出明确报错，避免运行期才暴露。
     */
    int MIN_KEY_LENGTH = 32;

    /** 调用方无法提供自身服务名时的兜底 {@code iss} 取值 */
    String DEFAULT_SERVICE_NAME = "unknown-service";

    /** 拒绝访问时的提示消息 */
    String REJECT_MESSAGE = "禁止访问：该接口仅允许服务间调用";

    /**
     * 拒绝访问时的响应体，与 core 侧 {@code JsonResultImpl} 的序列化形态保持一致
     * （{@code code / data / msg / businessCode}），网关注入与 SDK 过滤器共用这一份文本。
     */
    String REJECT_BODY = "{\"code\":403,\"data\":null,\"msg\":\""
            + REJECT_MESSAGE + "\",\"businessCode\":200}";

    /** 拒绝访问时使用的 HTTP 状态码 */
    int REJECT_STATUS = 403;

    /** 响应内容类型 */
    String REJECT_CONTENT_TYPE = "application/json;charset=utf-8";
}
