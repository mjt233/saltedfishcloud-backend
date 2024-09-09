package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAuthPlatform;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ThirdPartyAuthPlatformRepo extends BaseRepo<ThirdPartyAuthPlatform> {
    ThirdPartyAuthPlatform getByType(String type);

    @Query("SELECT p FROM ThirdPartyAuthPlatform p WHERE p.isEnable = true ")
    List<ThirdPartyAuthPlatform> findEnabled();
}
