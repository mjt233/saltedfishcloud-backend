package com.sfc.staticpublish.repo;

import com.sfc.staticpublish.model.po.StaticPublishRecord;
import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaticPublishRecordRepo extends BaseRepo<StaticPublishRecord> {

    /**
     * 按站点发布名称获取发布记录（发布方式：按主机名）
     * @param siteName   发布名称
     * @return              发布记录
     */
    @Query("SELECT r FROM StaticPublishRecord r WHERE r.siteName = :siteName AND r.accessWay = 1")
    StaticPublishRecord getBySiteName(@Param("siteName") String siteName);

    /**
     * 按发布用户+站点名称获取发布记录（发布方式：按路径）
     * @param username  用户名
     * @param siteName  站点名
     * @return          发布记录
     */
    @Query("SELECT r FROM StaticPublishRecord r WHERE r.username = :username AND r.siteName = :siteName AND r.accessWay = 2")
    StaticPublishRecord getByPath(@Param("username") String username, @Param("siteName") String siteName);

    /**
     * 按站点名称获取直接根目录发布的站点
     * @param siteName  站点名称
     * @return  发布记录
     */
    @Query("SELECT r FROM StaticPublishRecord r WHERE r.siteName = :siteName AND r.accessWay = 3")
    StaticPublishRecord getDirectRootPathBySiteName(@Param("siteName") String siteName);
}
