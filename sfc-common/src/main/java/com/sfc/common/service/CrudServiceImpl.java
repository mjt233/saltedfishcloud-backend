package com.sfc.common.service;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.service.CrudService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;

public abstract class CrudServiceImpl<T extends AuditModel, R extends BaseRepo<T>> implements CrudService<T> {
    @Autowired
    @Getter
    @Setter
    protected R repository;

    @Override
    public T findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public void save(T entity) {
        repository.save(entity);
    }

    @Override
    public void saveWithOwnerPermissions(T entity) {

        // 阻止篡改无权限的数据
        if (entity.getId() != null) {
            repository.findById(entity.getId()).ifPresent(existEntity -> {
                UIDValidator.validate(entity.getUid(), true);
            });
        }

        // 阻止创建不属于自己的数据
        if (entity.getUid() != null) {
            UIDValidator.validate(entity.getUid(), true);
        } else {
            entity.setUid(SecureUtils.getCurrentUid());
        }

        save(entity);
    }

    @Override
    public void deleteWithOwnerPermissions(Long id) {
        repository.findById(id).ifPresent(existEntity -> {
            UIDValidator.validate(id, true);
        });
        repository.deleteById(id);
    }

    @Override
    public void batchSave(Collection<T> entityList) {
        repository.saveAll(entityList);
    }

    @Override
    public List<T> findAll() {
        return repository.findAll();
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Override
    public CommonPageInfo<T> findByUid(Long uid, PageableRequest pageableRequest) {
        if (pageableRequest != null) {
            return CommonPageInfo.of(repository.findByUid(uid, PageRequest.of(pageableRequest.getPage(), pageableRequest.getSize())));
        } else {
            return CommonPageInfo.of(repository.findByUid(uid, null));
        }
    }

    @Override
    public CommonPageInfo<T> findByUidWithOwnerPermissions(Long uid, PageableRequest pageableRequest) {
        UIDValidator.validate(uid, false);
        return findByUid(uid, pageableRequest);
    }

    @Override
    public List<T> findByUid(Long uid) {
        return repository.findByUid(uid, null).getContent();
    }

    @Override
    public int batchDelete(Collection<Long> ids) {
        return repository.batchDelete(ids);
    }
}
