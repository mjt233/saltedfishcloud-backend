package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppPostLogoutRedirectUri;

import java.util.List;

/**
 * {@link ThirdPartyAppPostLogoutRedirectUri} 的 JPA Repository 接口。
 * <p>
 * 提供对 OIDC 客户端登出后重定向 URI 的增删改查操作。
 * </p>
 */
public interface ThirdPartyAppPostLogoutRedirectUriRepo extends BaseRepo<ThirdPartyAppPostLogoutRedirectUri> {

    /**
     * 根据所属应用 ID 查询所有登出后重定向 URI。
     *
     * @param appId 第三方应用 ID
     * @return 属于该应用的登出后重定向 URI 列表，若无则返回空列表
     */
    List<ThirdPartyAppPostLogoutRedirectUri> findByAppId(Long appId);

    /**
     * 根据所属应用 ID 删除所有登出后重定向 URI。
     *
     * @param appId 第三方应用 ID
     */
    void deleteByAppId(Long appId);
}
