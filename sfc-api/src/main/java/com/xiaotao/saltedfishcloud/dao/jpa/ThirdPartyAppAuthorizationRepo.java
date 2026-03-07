package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;

import java.util.Optional;

public interface ThirdPartyAppAuthorizationRepo extends BaseRepo<ThirdPartyAppAuthorization> {
    Optional<ThirdPartyAppAuthorization> findByAppIdAndUid(Long appId, Long uid);
}