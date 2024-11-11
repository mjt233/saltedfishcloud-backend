package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.service.CrudServiceImpl;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyPlatformUserRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyPlatformUser;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyPlatformUserServiceImpl
        extends CrudServiceImpl<ThirdPartyPlatformUser, ThirdPartyPlatformUserRepo>
        implements ThirdPartyPlatformUserService {

}
