package com.xiaotao.saltedfishcloud.service.third;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.service.CrudService;
import org.springframework.lang.Nullable;

public interface ThirdPartyAppService extends CrudService<ThirdPartyApp> {

    /**
     * 列出系统中已添加的第三方应用
     * @param pageableRequest 分页参数
     */
    CommonPageInfo<ThirdPartyApp> listApps(PageableRequest pageableRequest);
}