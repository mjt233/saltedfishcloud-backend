package com.sfc.cloud.trust;

import lombok.experimental.UtilityClass;

/**
 * 网关与微服务之间的「信任头」契约常量。
 */
@UtilityClass
public class SfcTrustHeaders {

    /**
     * 当前用户 uid 的请求头名：网关注入，微服务读取。
     * 凭证有效时为验签得到的用户 id，否则为 {@link #ANONYMOUS_UID}。
     */
    public static final String UID = "X-Sfc-Uid";

    /** 标识请求来自网关的请求头名，取值为 {@link #GATEWAY_NAME} */
    public static final String FROM_GATEWAY = "X-From-Gateway";

    /** {@link #FROM_GATEWAY} 的固定取值 */
    public static final String GATEWAY_NAME = "sfc-gateway";

    /** 无凭证 / 凭证无效时的 uid 取值，与公共资源 uid=0 的约定一致 */
    public static final String ANONYMOUS_UID = "0";
}
