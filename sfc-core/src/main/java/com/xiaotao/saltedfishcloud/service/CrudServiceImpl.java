package com.xiaotao.saltedfishcloud.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public abstract class CrudServiceImpl<T, R extends JpaRepository<T, Long>> implements CrudService<T> {
    @Autowired
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
}
