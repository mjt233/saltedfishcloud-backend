package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;

import java.util.Collection;
import java.util.List;

public interface CrudService<T> {
    T findById(Long id);

    void save(T entity);

    void batchSave(Collection<T> entityList);

    List<T> findAll();

    CommonPageInfo<T> findByUid(Long uid, PageableRequest pageableRequest);

    List<T> findByUid(Long uid);

    void delete(Long id);
}
