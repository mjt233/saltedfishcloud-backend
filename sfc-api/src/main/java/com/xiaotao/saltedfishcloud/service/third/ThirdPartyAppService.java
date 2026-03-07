package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.service.CrudService;

public interface ThirdPartyAppService extends CrudService<ThirdPartyApp> {

    /**
     * 列出系统中已添加的第三方应用
     * @param pageableRequest 分页参数
     */
    CommonPageInfo<ThirdPartyApp> listApps(PageableRequest pageableRequest);

    /**
     * 检查应用是否有效并获取应用信息。只有当应用存在且处于正常启用状态才正常返回对象，否则该方法会抛出异常。
     */
    ThirdPartyApp checkAndGetById(Long appId);
}