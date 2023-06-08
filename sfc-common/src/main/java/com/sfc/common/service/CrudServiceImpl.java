package com.sfc.common.service;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.service.CrudService;
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
        Page<T> queryResult = repository.findAll(
                (Specification<T>)(root, query, cb) -> query.where(cb.equal(root.get("uid"), uid)).getRestriction(),
                PageRequest.of(pageableRequest.getPage(), pageableRequest.getSize(), Sort.Direction.DESC, "id")
        );
        return CommonPageInfo.of(queryResult);
    }

    @Override
    public List<T> findByUid(Long uid) {
        return repository.findAll((Specification<T>)(root, query, cb) -> query.where(cb.equal(root.get("uid"), uid)).getRestriction());
    }
}
