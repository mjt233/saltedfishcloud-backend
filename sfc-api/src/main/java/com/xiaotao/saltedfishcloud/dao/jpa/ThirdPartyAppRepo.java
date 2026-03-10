package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ThirdPartyAppRepo extends BaseRepo<ThirdPartyApp> {
    
    /**
     * 根据名称查找应用（忽略大小写）
     */
    @Query("SELECT t FROM ThirdPartyApp t WHERE LOWER(t.name) = LOWER(:name)")
    Optional<ThirdPartyApp> findByNameIgnoreCase(@Param("name") String name);
    
    /**
     * 根据名称查找应用，排除指定ID（忽略大小写）
     */
    @Query("SELECT t FROM ThirdPartyApp t WHERE LOWER(t.name) = LOWER(:name) AND t.id != :excludeId")
    Optional<ThirdPartyApp> findByNameIgnoreCaseExcludingId(@Param("name") String name, @Param("excludeId") Long excludeId);
}