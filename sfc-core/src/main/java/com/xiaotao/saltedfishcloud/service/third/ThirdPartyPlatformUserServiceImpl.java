package com.xiaotao.saltedfishcloud.service.third;

import com.sfc.common.service.CrudServiceImpl;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyUserRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyPlatformUserServiceImpl
        extends CrudServiceImpl<ThirdPartyPlatformUser, ThirdPartyUserRepo>
        implements ThirdPartyPlatformUserService {

}
