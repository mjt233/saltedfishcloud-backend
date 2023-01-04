package com.xiaotao.saltedfishcloud.service.desktop;

import com.xiaotao.saltedfishcloud.dao.jpa.DesktopComponentConfigRepo;
import com.xiaotao.saltedfishcloud.model.po.DesktopComponentConfig;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class DesktopComponentConfigServiceImpl implements DesktopComponentConfigService {
    @Autowired
    private DesktopComponentConfigRepo repo;

    @Override
    public List<DesktopComponentConfig> listByUid(Long uid) {
        return Optional.ofNullable(repo.findByUid(uid)).orElseGet(Collections::emptyList);
    }

    @Override
    public void save(DesktopComponentConfig componentConfig) {
        if (componentConfig.getId() != null) {
            Long originId = repo.findById(componentConfig.getId()).orElseThrow(() -> new IllegalArgumentException("无效id")).getUid();
            if(!UIDValidator.validate(originId, true)) {
                throw new IllegalArgumentException("权限不足");
            }
        }
        if(!UIDValidator.validate(componentConfig.getUid(), true)) {
            throw new IllegalArgumentException("权限不足");
        }
        repo.save(componentConfig);

    }

    @Override
    public void remove(Long id) {
        DesktopComponentConfig config = repo.findById(id).orElseThrow();
        if(UIDValidator.validate(config.getUid(), true)) {
            repo.deleteById(id);
        } else {
            throw new IllegalArgumentException("权限不足");
        }
    }
}
