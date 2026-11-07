package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.UserCustomAttributeRepo;
import com.xiaotao.saltedfishcloud.model.UserCustomAttribute;
import org.springframework.stereotype.Service;
import com.sfc.cloud.data.service.SfcCrudServiceImpl;

@Service
public class UserCustomAttributeServiceImpl extends SfcCrudServiceImpl<UserCustomAttribute, UserCustomAttributeRepo> implements UserCustomAttributeService {


}
