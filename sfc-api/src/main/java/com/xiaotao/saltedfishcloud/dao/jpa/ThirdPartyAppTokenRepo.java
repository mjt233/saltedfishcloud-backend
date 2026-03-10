package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppToken;
import org.springframework.transaction.annotation.Transactional;

public interface ThirdPartyAppTokenRepo extends BaseRepo<ThirdPartyAppToken> {
    ThirdPartyAppToken findByAppIdAndUid(Long appId, Long uid);
}