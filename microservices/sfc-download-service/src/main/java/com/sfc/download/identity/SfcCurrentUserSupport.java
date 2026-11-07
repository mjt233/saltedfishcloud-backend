package com.sfc.download.identity;

import com.sfc.cloud.remote.client.SfcCoreUserClient;
import com.sfc.cloud.trust.SfcTrustHeaders;
import com.sfc.common.model.UserPrincipal;
import com.sfc.common.model.json.SfcJsonResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 离线下载微服务的「当前用户」身份支持。
 *
 * <p><b>背景：</b>本服务原为宿主插件 {@code sfc-ext-download}，身份取自宿主的 Spring Security 上下文
 * （{@code SecureUtils}）。迁移为独立微服务后本服务<b>没有</b> Spring Security，身份只有一个来源：
 * 网关注入的信任头 {@link SfcTrustHeaders#UID}（{@code X-Sfc-Uid}）。本类把该口径收敛成一处，
 * 供控制器与共享基座的 uid 校验器共用。</p>
 *
 * <p><b>匿名口径：</b>网关对「无凭证 / 凭证无效」的请求注入 {@link SfcTrustHeaders#ANONYMOUS_UID}
 * （{@code "0"}），本类将其映射为 {@code null}（未登录），与宿主
 * {@code SecureUtils.getCurrentUid()} 在未认证时返回 {@code null} 的口径一致，
 * 从而保证 {@code SfcUIDValidator} 对公共资源「读放行、写要求管理员」的判定不变。</p>
 *
 * <p><b>管理员判定：</b>本服务没有 Spring Security，无法自行判定管理员。管理员结果经
 * {@link SfcCoreUserClient} 回源到 core（{@code GET /api/user}，由 Feign 链路透传入站请求的用户凭证），
 * 回源结果按 uid 做短 TTL 缓存，避免 {@code SfcUIDValidator} 热路径反复发起远程调用。
 * 任何失败（无客户端 Bean、网络异常、core 返回非 200、uid 不一致）一律降级为
 * {@code false}（fail-closed），绝不抛出异常。</p>
 *
 * <p><b>为什么是静态工具类：</b>身份消费者是静态的 {@code CurrentUserHolder} SPI 与
 * {@code SfcUIDValidator}，调用点无法依赖注入；因此与仓库既有的
 * {@code SfcMapperHolder} / {@code CurrentUserHolder} 保持一致，采用「静态工具 + 启动期注册可选依赖」
 * 的形态。注册见 {@link SfcDownloadIdentityConfiguration}。</p>
 *
 * @see SfcCurrentUserProvider
 * @see SfcDownloadIdentityConfiguration
 */
@Slf4j
public final class SfcCurrentUserSupport {

    /** 管理员判定成功（{@code true}）结果的缓存时长（毫秒） */
    private static final long ADMIN_TRUE_TTL_MILLIS = 60_000L;

    /**
     * 管理员判定为 {@code false} 的缓存时长（毫秒）。
     *
     * <p>该结果同时覆盖「确为非管理员」与「回源失败」两种情形，因此取较短时长：
     * 既避免偶发失败被长期固化，也不让管理员权限的变更延迟过久生效。</p>
     */
    private static final long ADMIN_FALSE_TTL_MILLIS = 10_000L;

    /** 缓存条目上限，超过后清理过期条目，避免长期运行下无界增长 */
    private static final int MAX_CACHE_ENTRIES = 1024;

    /** 远程用户客户端（可选依赖）；由启动期 {@link #setUserClientProvider(ObjectProvider)} 注册 */
    private static volatile ObjectProvider<SfcCoreUserClient> userClientProvider;

    /** uid → 管理员判定缓存 */
    private static final Map<Long, AdminCacheEntry> ADMIN_CACHE = new ConcurrentHashMap<>();

    /** 工具类不允许实例化 */
    private SfcCurrentUserSupport() {
    }

    /**
     * 注册远程用户客户端（启动期调用一次）。
     *
     * @param provider 远程用户客户端的可选提供者；传 {@code null} 表示关闭管理员回源
     *                 （管理员判定将恒为 {@code false}）
     */
    public static void setUserClientProvider(@Nullable ObjectProvider<SfcCoreUserClient> provider) {
        userClientProvider = provider;
        if (provider == null) {
            log.info("[身份] 未注册远程用户客户端，管理员判定将恒为 false（uid 不受影响）");
        } else {
            log.info("[身份] 已注册远程用户客户端，管理员判定将回源到 sfc-core");
        }
    }

    // ── 当前用户读取 ──

    /**
     * 获取当前登录用户的 uid。
     *
     * @return 当前登录用户 uid；未登录（匿名）、无 HTTP 上下文或信任头非法时返回 {@code null}
     */
    @Nullable
    public static Long getCurrentUid() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return parseUidHeader(request.getHeader(SfcTrustHeaders.UID));
    }

    /**
     * 判断当前登录用户是否为系统管理员。
     *
     * <p>本方法<b>不会</b>向外抛异常：无身份、无 HTTP 上下文、远程回源失败一律返回 {@code false}
     * （fail-closed，与 {@code CurrentUserHolder} 的失效策略一致）。</p>
     *
     * @return 是管理员返回 {@code true}；其余情况返回 {@code false}
     */
    public static boolean currentIsAdmin() {
        Long uid = getCurrentUid();
        if (uid == null) {
            // 未登录 / 匿名：直接 fail-closed，且省下一次远程回源
            return false;
        }
        long now = System.currentTimeMillis();
        AdminCacheEntry cached = ADMIN_CACHE.get(uid);
        if (cached != null && cached.expireAt() > now) {
            return cached.admin();
        }
        boolean admin = resolveAdmin(uid);
        ADMIN_CACHE.put(uid, new AdminCacheEntry(admin,
                now + (admin ? ADMIN_TRUE_TTL_MILLIS : ADMIN_FALSE_TTL_MILLIS)));
        evictExpiredIfNeeded(now);
        return admin;
    }

    /**
     * 获取当前线程绑定的 HTTP 请求。
     *
     * @return 当前请求；无 HTTP 上下文（定时任务线程、非 Web 线程）时返回 {@code null}
     */
    @Nullable
    public static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /**
     * 解析信任头的原始取值。
     *
     * @param rawUid 信任头原文；可为 {@code null}
     * @return 登录用户 uid；信任头缺失、为 {@link SfcTrustHeaders#ANONYMOUS_UID}（匿名）、
     * 非法或为负数时返回 {@code null}
     */
    @Nullable
    public static Long parseUidHeader(@Nullable String rawUid) {
        if (rawUid == null || rawUid.isBlank()) {
            return null;
        }
        String trimmed = rawUid.trim();
        if (SfcTrustHeaders.ANONYMOUS_UID.equals(trimmed)) {
            // 无凭证 / 凭证无效：按「未登录」处理，与宿主 SecureUtils.getCurrentUid() 的 null 口径对齐
            return null;
        }
        try {
            long uid = Long.parseLong(trimmed);
            return uid < 0 ? null : uid;
        } catch (NumberFormatException e) {
            log.debug("[身份] 信任头 {} 取值非法，按未登录处理", SfcTrustHeaders.UID);
            return null;
        }
    }

    // ── 内部实现 ──

    /**
     * 回源到 core 判定指定用户是否为管理员，并吞掉一切异常（热路径安全保证）。
     *
     * @param uid 用户 uid
     * @return 是管理员返回 {@code true}；客户端不存在、调用失败、响应异常或 uid 不一致时返回 {@code false}
     */
    private static boolean resolveAdmin(Long uid) {
        ObjectProvider<SfcCoreUserClient> provider = userClientProvider;
        if (provider == null) {
            return false;
        }
        try {
            SfcCoreUserClient client = provider.getIfAvailable();
            if (client == null) {
                return false;
            }
            SfcJsonResult<UserPrincipal> envelope = client.getCurrentUser();
            if (envelope == null || envelope.getCode() != 200 || envelope.getData() == null) {
                log.debug("[身份] 管理员回源业务失败，降级为非管理员: uid={}, code={}",
                        uid, envelope == null ? "null" : envelope.getCode());
                return false;
            }
            UserPrincipal profile = envelope.getData();
            if (profile.getId() == null || !profile.getId().equals(uid)) {
                // 远程接口按入站凭证识别调用者，与信任头 uid 必须自洽；不自洽说明凭证与信任头不是同一人
                log.warn("[身份] 管理员回源结果的 uid 与信任头不一致，降级为非管理员: headerUid={}, remoteUid={}",
                        uid, profile.getId());
                return false;
            }
            return profile.isAdmin();
        } catch (RuntimeException e) {
            // core 无登录态返回 401、网络/负载均衡失败等都会走到这里；一律降级，绝不让异常击穿权限校验
            log.warn("[身份] 管理员回源调用异常，降级为非管理员: uid={}, cause={}", uid, e.toString());
            return false;
        }
    }

    /**
     * 缓存条目数超限时清理已过期条目。
     *
     * @param now 当前时间戳（毫秒）
     */
    private static void evictExpiredIfNeeded(long now) {
        if (ADMIN_CACHE.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        ADMIN_CACHE.entrySet().removeIf(entry -> entry.getValue().expireAt() <= now);
    }

    /**
     * 管理员判定缓存条目。
     *
     * @param admin    是否为管理员
     * @param expireAt 过期时间戳（毫秒）
     */
    private record AdminCacheEntry(boolean admin, long expireAt) {
    }
}
