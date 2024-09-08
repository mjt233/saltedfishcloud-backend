package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;

public interface ThirdPartyAuthPlatformRepo extends BaseRepo<ThirdPartyAuthPlatform> {
    ThirdPartyAuthPlatform getByType(String type);
}
