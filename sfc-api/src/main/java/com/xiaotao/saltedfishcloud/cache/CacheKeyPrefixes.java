package com.xiaotao.saltedfishcloud.cache;

/**
 * 缓存 key 前缀常量类。
 * <p>统一使用 {@code sfc:{module}:{业务标识}} 格式，避免各业务类硬编码前缀导致的不一致问题。</p>
 */
public final class CacheKeyPrefixes {
    /** 用户 Token 缓存前缀 */
    public static final String TOKEN = "sfc:token:";
    /** 邮箱验证码缓存前缀 */
    public static final String MAIL_VALIDATE = "sfc:user:mailValidate:";
    /** 注册邀请码缓存前缀 */
    public static final String REG_MAIL = "sfc:user:regMail:";
    /** 打包下载任务缓存前缀 */
    public static final String WRAP = "sfc:wrap:";
    /** 断点续传任务元数据缓存前缀 */
    public static final String BREAKPOINT_META = "sfc:breakpoint:";
    /** 断点续传已完成分片缓存前缀 */
    public static final String BREAKPOINT_FINISH = "sfc:breakpoint:finish:";
    /** 异步任务进度缓存前缀 */
    public static final String TASK_PROGRESS = "sfc:task:progress:";
    /** 异步任务执行权缓存前缀 */
    public static final String TASK_HOLD = "sfc:task:hold:";
    /** 集群节点信息缓存前缀 */
    public static final String CLUSTER = "sfc:cluster:";
    /** OAuth 授权码缓存前缀 */
    public static final String OAUTH_AUTH_CODE = "sfc:oauth:authCode:";
    /** OAuth Ticket 黑名单缓存前缀 */
    public static final String OAUTH_DISABLED_TICKET = "sfc:oauth:disabledTicket:";
    /** OAuth 回调幂等控制缓存前缀 */
    public static final String OAUTH_CALLBACK = "sfc:oauth:callback:";
    /** 第三方平台登录行为缓存前缀 */
    public static final String THIRD_ACTION = "sfc:third:action:";
    /** 快速分享提取码缓存前缀 */
    public static final String QUICK_SHARE = "sfc:share:";
    /** 视频播放进度缓存前缀 */
    public static final String WATCH_PROGRESS = "sfc:video:progress:";
    /** 文件临时下载链接缓存前缀 */
    public static final String FILE_LINK = "sfc:fileLink:";

    private CacheKeyPrefixes() {}
}
