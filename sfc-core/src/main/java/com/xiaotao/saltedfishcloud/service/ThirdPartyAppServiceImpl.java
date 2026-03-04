package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ThirdPartyAppServiceImpl extends CrudServiceImpl<ThirdPartyApp, ThirdPartyAppRepo> implements ThirdPartyAppService {

    @Override
    public CommonPageInfo<ThirdPartyApp> listApps(PageableRequest pageableRequest) {
        if (pageableRequest == null || pageableRequest.getSize() == null || pageableRequest.getPage() == null) {
            throw new JsonException("缺少 page 或 size 参数");
        }
        return CommonPageInfo.of(getRepository().findAll(
                JpaLambdaQueryWrapper.get(ThirdPartyApp.class).build(),
                PageRequest.of(pageableRequest.getPage(), pageableRequest.getSize())
        ));
    }
}