package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.UserCustomAttributeRepo;
import com.xiaotao.saltedfishcloud.model.UserCustomAttribute;
import org.springframework.stereotype.Service;

@Service
public class UserCustomAttributeServiceImpl extends CrudServiceImpl<UserCustomAttribute, UserCustomAttributeRepo> implements UserCustomAttributeService {


}
