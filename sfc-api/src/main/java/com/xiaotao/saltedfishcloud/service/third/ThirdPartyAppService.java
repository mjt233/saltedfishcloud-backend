package com.xiaotao.saltedfishcloud.service.third;

import com.sfc.common.model.SfcPageInfo;
import com.sfc.common.model.param.SfcPageableRequest;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.sfc.cloud.data.service.SfcCrudService;

public interface ThirdPartyAppService extends SfcCrudService<ThirdPartyApp> {

    /**
     * 列出系统中已添加的第三方应用
     * @param pageableRequest 分页参数
     */
    SfcPageInfo<ThirdPartyApp> listApps(SfcPageableRequest pageableRequest);

    /**
     * 检查应用是否有效并获取应用信息。只有当应用存在且处于正常启用状态才正常返回对象，否则该方法会抛出异常。
     */
    ThirdPartyApp checkAndGetById(Long appId);
}