package com.sfc.staticpublish.service;

import com.sfc.staticpublish.model.po.StaticPublishRecord;
import com.xiaotao.saltedfishcloud.service.CrudService;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaticPublishRecordService extends CrudService<StaticPublishRecord> {

    /**
     * 按站点发布名称获取发布记录（发布方式：按主机名）
     * @param publishName   发布名称
     * @return              发布记录
     */
    StaticPublishRecord getBySiteName(String publishName);

    /**
     * 按发布用户+站点名称获取发布记录（发布方式：按路径）
     * @param username  用户名
     * @param siteName  站点名
     * @return          发布记录
     */
    StaticPublishRecord getByPath(String username, String siteName);

    /**
     * 按站点名称获取直接根目录发布的站点
     * @param siteName  站点名称
     * @return  发布记录
     */
    StaticPublishRecord getDirectRootPathBySiteName(String siteName);

}
