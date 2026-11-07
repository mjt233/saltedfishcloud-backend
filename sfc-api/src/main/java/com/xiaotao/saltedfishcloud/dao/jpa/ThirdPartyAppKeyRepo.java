package com.xiaotao.saltedfishcloud.dao.jpa;

import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ThirdPartyAppKeyRepo extends SfcBaseRepo<ThirdPartyAppKey> {
    List<ThirdPartyAppKey> findByAppId(Long appId);
}