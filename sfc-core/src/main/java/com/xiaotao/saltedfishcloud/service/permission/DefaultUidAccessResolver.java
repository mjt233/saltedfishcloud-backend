package com.xiaotao.saltedfishcloud.service.permission;

import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认的 UID 访问级别解析器。
 * <p>
 * 聚合容器中所有 {@link UidAccessProvider} Bean（如团队空间插件提供的实现），
 * 应用启动时经静态持有器注册到 sfc-api 的 {@link UidAccessResolver}，
 * 供 UIDValidator 与 UidAccessValidator 统一调用。
 * 未安装任何提供者时行为与旧版完全一致（仅本人/管理员/公共资源规则）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultUidAccessResolver implements UidAccessResolver {

    /**
     * 全部 UID 访问权提供者（可为空列表）
     */
    private final List<UidAccessProvider> providers;

    /**
     * 启动时注册到全局静态持有器
     */
    @PostConstruct
    public void init() {
        UidAccessResolver.setInstance(this);
        log.info("UidAccessResolver 已注册，提供者数量：{}", providers == null ? 0 : providers.size());
    }

    /**
     * {@inheritDoc}
     * <p>
     * 解析优先级：匿名=无权限；管理员=MANAGE；公共资源（uid=0）=READ；
     * 本人=MANAGE；其余取所有提供者返回的最高级别（异常按 NONE 处理）。
     */
    @Override
    public UidAccessLevel getAccessLevel(UserPrincipal user, long uid) {
        if (user == null) {
            return UidAccessLevel.NONE;
        }
        if (user.isAdmin()) {
            return UidAccessLevel.MANAGE;
        }
        if (uid == UserConstants.PUBLIC_USER_ID) {
            return UidAccessLevel.READ;
        }
        if (user.getId() != null && user.getId() == uid) {
            return UidAccessLevel.MANAGE;
        }
        UidAccessLevel best = UidAccessLevel.NONE;
        if (providers != null) {
            for (UidAccessProvider provider : providers) {
                try {
                    UidAccessLevel level = provider.getAccessLevel(user, uid);
                    if (level != null && level.getOrder() > best.getOrder()) {
                        best = level;
                        if (best == UidAccessLevel.MANAGE) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    // 提供者异常不影响主流程，按无该提供商权限处理
                    log.warn("UidAccessProvider 查询异常：{} - {}", uid, e.getMessage());
                }
            }
        }
        return best;
    }
}
