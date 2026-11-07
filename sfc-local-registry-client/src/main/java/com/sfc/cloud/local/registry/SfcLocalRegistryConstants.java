package com.sfc.cloud.local.registry;

/**
 * local 注册中心客户端与服务端共享的协议常量。
 */
public final class SfcLocalRegistryConstants {
    /** 注册中心 API 路径前缀 */
    public static final String API_PREFIX = "/api/local-registry";

    /** 注册密码请求头 */
    public static final String PASSWORD_HEADER = "X-Sfc-Local-Registry-Password";

    /** 注册实例接口路径 */
    public static final String PATH_REGISTER = "/register";

    /** 心跳续约接口路径 */
    public static final String PATH_HEARTBEAT = "/heartbeat";

    /** 注销实例接口路径 */
    public static final String PATH_DEREGISTER = "/deregister";

    /** 实例查询接口路径 */
    public static final String PATH_INSTANCES = "/instances";

    /** 服务名列表接口路径 */
    public static final String PATH_SERVICES = "/services";

    private SfcLocalRegistryConstants() {
    }
}
