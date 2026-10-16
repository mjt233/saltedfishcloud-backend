package com.xiaotao.saltedfishcloud.dao.jpa;

import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;

public interface ThirdPartyPlatformUserRepo extends SfcBaseRepo<ThirdPartyPlatformUser> {
    ThirdPartyPlatformUser getByPlatformTypeAndThirdPartyUserId(String platformType, String thirdPartyUserId);

    ThirdPartyPlatformUser getByPlatformTypeAndUid(String platformType, Long uid);
}
