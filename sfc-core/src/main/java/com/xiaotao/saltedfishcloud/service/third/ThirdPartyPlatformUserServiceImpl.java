package com.xiaotao.saltedfishcloud.service.third;

import com.sfc.cloud.data.service.SfcCrudServiceImpl;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyPlatformUserRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyPlatformUserServiceImpl
        extends SfcCrudServiceImpl<ThirdPartyPlatformUser, ThirdPartyPlatformUserRepo>
        implements ThirdPartyPlatformUserService {

}
