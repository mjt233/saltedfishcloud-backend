package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppKeyRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppKeyService;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyAppKeyServiceImpl extends CrudServiceImpl<ThirdPartyAppKey, ThirdPartyAppKeyRepo> implements ThirdPartyAppKeyService {
}