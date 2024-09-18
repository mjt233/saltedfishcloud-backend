package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;

public interface ThirdPartyPlatformUserRepo extends BaseRepo<ThirdPartyPlatformUser> {
    ThirdPartyPlatformUser getByPlatformTypeAndThirdPartyUserId(String platformType, String thirdPartyUserId);

    ThirdPartyPlatformUser getByPlatformTypeAndUid(String platformType, Long uid);
}
