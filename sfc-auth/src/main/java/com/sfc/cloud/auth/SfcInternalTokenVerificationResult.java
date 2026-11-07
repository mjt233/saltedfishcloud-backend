package com.sfc.cloud.auth;

/**
 * 内部调用凭证的校验结果。
 *
 * <p>校验器<b>不抛异常</b>：所有失败都以 {@link Failure} 表达，便于调用方决定 HTTP 语义与日志级别。</p>
 *
 * @param valid   是否校验通过
 * @param failure 失败原因；{@code valid} 为 true 时为 null
 * @param issuer  凭证中的调用方服务名（{@code iss}）；无法解析时为 null
 * @param jti     凭证的唯一标识（{@code jti}）；无法解析时为 null
 */
public record SfcInternalTokenVerificationResult(boolean valid, Failure failure, String issuer, String jti) {

    /**
     * 校验失败的原因。描述文本用于日志，<b>不</b>回给调用方（对外统一是 403 + 固定消息，避免泄露判定细节）。
     */
    public enum Failure {

        /** 服务端未配置共享密钥：fail-closed 拒绝一切 */
        KEY_NOT_CONFIGURED("未配置服务间共享密钥"),

        /** 请求未携带凭证请求头 */
        MISSING_TOKEN("请求未携带内部调用凭证"),

        /** 凭证结构非法、算法不受支持或签名验证失败 */
        MALFORMED("凭证结构非法或签名算法不受支持"),

        /** 验签失败（签名不符） */
        BAD_SIGNATURE("验签失败"),

        /** 凭证已过期 */
        EXPIRED("凭证已过期"),

        /** 凭证尚未生效（时钟偏移之外） */
        NOT_YET_VALID("凭证尚未生效"),

        /** 缺少必需声明（jti / exp / iss / sub） */
        MISSING_CLAIM("凭证缺少必需声明"),

        /** 调用方服务名不在白名单内 */
        ISSUER_NOT_ALLOWED("调用方服务名不在允许名单内");

        /** 日志用描述 */
        private final String description;

        /**
         * 构造失败原因。
         *
         * @param description 日志用描述
         */
        Failure(String description) {
            this.description = description;
        }

        /**
         * 获取日志用描述。
         *
         * @return 失败原因的中文描述
         */
        public String getDescription() {
            return description;
        }
    }

    /**
     * 构造"校验通过"结果。
     *
     * @param issuer 调用方服务名
     * @param jti    凭证唯一标识
     * @return 校验结果
     */
    public static SfcInternalTokenVerificationResult success(String issuer, String jti) {
        return new SfcInternalTokenVerificationResult(true, null, issuer, jti);
    }

    /**
     * 构造"校验失败"结果。
     *
     * @param failure 失败原因
     * @param issuer  已解析出的调用方服务名，可为 null
     * @param jti     已解析出的凭证唯一标识，可为 null
     * @return 校验结果
     */
    public static SfcInternalTokenVerificationResult failure(Failure failure, String issuer, String jti) {
        return new SfcInternalTokenVerificationResult(false, failure, issuer, jti);
    }
}
