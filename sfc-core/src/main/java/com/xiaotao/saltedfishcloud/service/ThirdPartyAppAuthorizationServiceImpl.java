package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppAuthorizationRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyAppAuthorizationServiceImpl extends CrudServiceImpl<ThirdPartyAppAuthorization, ThirdPartyAppAuthorizationRepo> implements ThirdPartyAppAuthorizationService {
}