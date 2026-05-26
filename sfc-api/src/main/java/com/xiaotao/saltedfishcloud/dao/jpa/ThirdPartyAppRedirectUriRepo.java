package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppRedirectUri;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link ThirdPartyAppRedirectUri} 的 JPA Repository 接口。
 * <p>
 * 提供对 OIDC 客户端重定向 URI 的增删改查操作。
 * </p>
 */
public interface ThirdPartyAppRedirectUriRepo extends BaseRepo<ThirdPartyAppRedirectUri> {

    /**
     * 根据所属应用 ID 查询所有重定向 URI。
     *
     * @param appId 第三方应用 ID
     * @return 属于该应用的重定向 URI 列表，若无则返回空列表
     */
    List<ThirdPartyAppRedirectUri> findByAppId(Long appId);

    /**
     * 根据所属应用 ID 批量删除所有重定向 URI。
     * <p>
     * 使用显式 JPQL DELETE 语句直接执行批量删除，避免 Spring Data 派生方法的
     * "先加载再逐条删除"行为。
     * </p>
     *
     * @param appId 第三方应用 ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ThirdPartyAppRedirectUri r WHERE r.appId = :appId")
    void deleteByAppId(@Param("appId") Long appId);
}
