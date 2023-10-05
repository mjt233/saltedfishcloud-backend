package com.sfc.staticpublish.service.impl;

import com.sfc.common.service.CrudServiceImpl;
import com.sfc.staticpublish.constants.AccessWay;
import com.sfc.staticpublish.constants.CacheNames;
import com.sfc.staticpublish.model.po.StaticPublishRecord;
import com.sfc.staticpublish.repo.StaticPublishRecordRepo;
import com.sfc.staticpublish.service.StaticPublishRecordService;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StaticPublishRecordServiceImpl extends CrudServiceImpl<StaticPublishRecord, StaticPublishRecordRepo> implements StaticPublishRecordService {
    @Autowired
    private UserService userService;

    @Autowired
    private CacheManager cacheManager;

    @Override
    @Cacheable(cacheNames = CacheNames.STATIC_PUBLISH, key = "'byName::' + #publishName")
    public StaticPublishRecord getBySiteName(String publishName) {
        return repository.getBySiteName(publishName);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.STATIC_PUBLISH, key = "'byPath::' + #username + '::' + #siteName")
    public StaticPublishRecord getByPath(String username, String siteName) {
        return getRepository().getByPath(username, siteName);
    }

    @Override
    public void save(StaticPublishRecord entity) {
        User user = userService.getUserById(entity.getUid().intValue());
        entity.setUsername(user.getUsername());

        if (entity.isByHost()) {
            StaticPublishRecord existRecord = repository.getBySiteName(entity.getSiteName());
            if (existRecord != null && !existRecord.getId().equals(entity.getId())) {
                throw new JsonException("站点名称冲突，请尝试其他站点名称");
            }
        } else {
            StaticPublishRecord existRecord = repository.getByPath(entity.getUsername(), entity.getSiteName());
            if (existRecord != null && !existRecord.getId().equals(entity.getId())) {
                throw new JsonException("站点名称冲突，请尝试其他站点名称");
            }
        }
        super.save(entity);
        removeCache(entity);
    }

    @Override
    public void deleteWithOwnerPermissions(Long id) {
        super.deleteWithOwnerPermissions(id);
        removeCache(findById(id));
    }

    @Override
    public void batchSave(Collection<StaticPublishRecord> entityList) {
        Map<Long, User> userMap = userService.findBaseInfoByIds(entityList.stream().map(AuditModel::getUid).distinct().collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(e -> e.getId().longValue(), Function.identity()));
        for (StaticPublishRecord record : entityList) {
            record.setUsername(userMap.get(record.getUid()).getUser());
        }
        super.batchSave(entityList);
        entityList.forEach(this::removeCache);
    }

    private void removeCache(StaticPublishRecord entity) {
        Cache cache = cacheManager.getCache(CacheNames.STATIC_PUBLISH);
        if (cache == null || entity == null) {
            return;
        }
        if (Objects.equals(AccessWay.BY_HOST, entity.getAccessWay())) {
            cache.evict("byName::" + entity.getSiteName());
        } else {
            cache.evict("byPath::" + entity.getUsername() + "::" + entity.getSiteName());
        }
    }
}
