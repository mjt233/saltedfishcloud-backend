package com.xiaotao.saltedfishcloud.annotations;

/**
 * 实体类配置项命名策略
 */
public enum ConfigKeyNameStrategy {
    /**
     * 短横杠命名法<br>
     * e.g. "secret-key"
     */
    KEBAB_CASE,

    /**
     * 下划线命名法<br>
     * e.g. "secret_key"
     */
    UNDER_SCORE_CASE,

    /**
     * 小驼峰命名法<br>
     * e.g. "secretKey"
     */
    CAMEL_CASE,

    /**
     * 继承上级配置。若无上级则默认为{@link #KEBAB_CASE}
     */
    INHERIT
}
