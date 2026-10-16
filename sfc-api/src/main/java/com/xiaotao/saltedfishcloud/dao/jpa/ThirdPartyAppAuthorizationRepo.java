package com.xiaotao.saltedfishcloud.dao.jpa;

import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ThirdPartyAppAuthorizationRepo extends SfcBaseRepo<ThirdPartyAppAuthorization> {
    Optional<ThirdPartyAppAuthorization> findByAppIdAndUid(Long appId, Long uid);

    @Transactional
    void deleteByAppIdAndUid(Long appId, Long uid);
}