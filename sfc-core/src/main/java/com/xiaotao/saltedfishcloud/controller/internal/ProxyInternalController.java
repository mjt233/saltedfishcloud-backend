package com.xiaotao.saltedfishcloud.controller.internal;

import com.sfc.cloud.model.SfcProxyInfo;
import com.sfc.common.constant.SfcUserConstants;
import com.sfc.common.model.json.SfcJsonResult;
import com.xiaotao.saltedfishcloud.annotations.AllowAnonymous;
import com.xiaotao.saltedfishcloud.model.po.ProxyInfo;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.ProxyInfoService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 代理节点内部控制器。仅用于微服务系统内部调用。
 *
 * <p>{@value #PREFIX} 属于 {@code /api/internal/**} 前缀，由 {@code sfc-auth} SDK 的
 * {@code SfcInternalAuthFilter} 强制校验服务间调用凭证（请求头 {@code X-Sfc-Internal-Token} 中
 * 每次调用新签发的 HS256 JWT），客户端无法直接访问；网关侧也会直接拒绝该前缀。
 * 因此这里的 {@code @AllowAnonymous} 表达的是"调用方是服务、没有用户登录态"，而不是"对所有人开放"。</p>
 *
 * <p>与公开接口 {@code GET /api/proxy/available} 的区别：公开接口依据调用方的 Spring Security
 * 登录态判定身份，本接口则由调用方显式传入目标用户 ID，供没有用户上下文的微服务
 * （如离线下载服务）按用户查询可用代理节点。</p>
 */
@RestController
@RequestMapping(ProxyInternalController.PREFIX)
@Validated
@RequiredArgsConstructor
@Tag(name = "代理节点-sfc-core微服务间内部接口")
public class ProxyInternalController {

    /** 接口前缀 */
    public static final String PREFIX = "/api/internal/proxy";

    /** 代理节点服务 */
    private final ProxyInfoService proxyInfoService;

    /** 用户服务，用于判定目标用户是否为管理员 */
    private final UserService userService;

    /**
     * 按用户查询可用代理节点。
     *
     * <p>可见范围与公开接口保持一致：公共代理（{@code uid=0}）对所有用户可见，用户私有代理仅对其本人可见；
     * 两类来源按代理 id 去重（公共代理在前，本人代理在后）。</p>
     *
     * <p>受保护的公共代理（{@code isProtect=true}，只允许系统内部功能或管理员使用）仅对管理员返回，
     * 非管理员（含匿名用户 {@code uid=0} 与不存在的用户）会被过滤掉。判断管理员身份时以本方法
     * 入参 {@code uid} 为准，而不是调用方链路上的登录态。</p>
     *
     * @param uid 目标用户 ID，0 表示匿名/公共用户（此时只查询公共代理，且按非管理员过滤受保护代理）
     * @return 该用户可见的代理节点列表，按「公共代理 → 本人代理」顺序返回，可能为空列表
     */
    @GetMapping("available")
    @AllowAnonymous
    public SfcJsonResult<List<SfcProxyInfo>> listAvailable(@RequestParam("uid") long uid) {
        // 按 id 去重的合并结果：先公共代理，后本人代理，保持稳定顺序
        Map<Long, ProxyInfo> merged = new LinkedHashMap<>();
        for (ProxyInfo proxy : proxyInfoService.findByUid(SfcUserConstants.PUBLIC_USER_ID)) {
            merged.putIfAbsent(proxy.getId(), proxy);
        }
        // uid=0 时公共代理即本人代理，无需重复查询
        if (uid != SfcUserConstants.PUBLIC_USER_ID) {
            for (ProxyInfo proxy : proxyInfoService.findByUid(uid)) {
                merged.putIfAbsent(proxy.getId(), proxy);
            }
        }

        // 非管理员不可见受保护的公共代理；用户不存在（或 uid=0）时一律按非管理员处理
        User user = userService.getUserById(uid);
        boolean isAdmin = user != null && user.isAdmin();

        List<SfcProxyInfo> result = new ArrayList<>(merged.size());
        for (ProxyInfo proxy : merged.values()) {
            boolean isPublicProxy = Objects.equals(SfcUserConstants.PUBLIC_USER_ID, proxy.getUid());
            if (isPublicProxy && Boolean.TRUE.equals(proxy.getIsProtect()) && !isAdmin) {
                continue;
            }
            result.add(toSfcProxyInfo(proxy));
        }
        return SfcJsonResult.getInstance(result);
    }

    /**
     * 把宿主代理实体转换为微服务间共享的代理信息模型。
     *
     * @param proxy 宿主代理实体
     * @return 共享模型实例
     */
    private static SfcProxyInfo toSfcProxyInfo(ProxyInfo proxy) {
        return SfcProxyInfo.builder()
                .id(proxy.getId())
                .name(proxy.getName())
                .type(proxy.getType() == null ? null : proxy.getType().name())
                .address(proxy.getAddress())
                .port(proxy.getPort())
                .build();
    }
}
