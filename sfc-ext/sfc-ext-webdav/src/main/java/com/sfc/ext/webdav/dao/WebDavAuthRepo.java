package com.sfc.ext.webdav.dao;

import com.sfc.ext.webdav.model.po.WebDavAuth;
import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface WebDavAuthRepo extends BaseRepo<WebDavAuth> {
    @Query("SELECT wda FROM WebDavAuth wda WHERE wda.uid = :uid")
    WebDavAuth findOneByUid(@Param("uid") Long uid);

    @Query("SELECT wda FROM WebDavAuth wda WHERE wda.username = :username")
    WebDavAuth findOneByUsername(@Param("username") String username);
}
