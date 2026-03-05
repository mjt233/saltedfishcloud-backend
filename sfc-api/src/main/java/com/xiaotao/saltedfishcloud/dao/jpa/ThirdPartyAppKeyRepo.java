package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ThirdPartyAppKeyRepo extends BaseRepo<ThirdPartyAppKey> {
    List<ThirdPartyAppKey> findByAppId(Long appId);

    @Query("SELECT k FROM ThirdPartyAppKey k WHERE k.appId = :appId AND k.clientSecretHash = :clientSecretHash")
    ThirdPartyAppKey findByAppIdAndSecret(@Param("appId") Long appId,
                                          @Param("clientSecretHash") String clientSecretHash
    );
}