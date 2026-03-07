package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppRepo;
import com.xiaotao.saltedfishcloud.event.oauth.ThirdPartyAppDeleteEvent;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.utils.db.JpaLambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ThirdPartyAppServiceImpl extends CrudServiceImpl<ThirdPartyApp, ThirdPartyAppRepo> implements ThirdPartyAppService {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void save(ThirdPartyApp entity) {
        checkNameConflict(entity);
        super.save(entity);
    }

    private void checkNameConflict(ThirdPartyApp app) {
        boolean exists = repository.exists(JpaLambdaQueryWrapper.get(ThirdPartyApp.class)
                .eq(ThirdPartyApp::getName, app.getName())
                .ne(ThirdPartyApp::getId, app.getId())
                .build()
        );
        if (exists) {
            throw new JsonException("已存在同名应用");
        }
    }

    @Override
    @Transactional
    public int batchDelete(Collection<Long> ids) {
        List<ThirdPartyApp> apps = repository.findByIds(ids);
        if (apps.isEmpty()) {
            return 0;
        }
        int res = super.batchDelete(ids);
        applicationEventPublisher.publishEvent(new ThirdPartyAppDeleteEvent(this, apps));
        return res;
    }

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

    @Override
    public ThirdPartyApp checkAndGetById(Long appId) {
        ThirdPartyApp app = findById(appId);
        if (app == null) {
            throw new JsonException("无效的appId");
        }
        if (!Boolean.TRUE.equals(app.getIsEnabled())) {
            throw new JsonException("该应用已被停用");
        }
        return app;
    }
}