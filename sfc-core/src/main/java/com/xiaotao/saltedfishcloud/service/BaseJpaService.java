package com.xiaotao.saltedfishcloud.service;

import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseJpaService<T> {
    @Autowired
    protected T repo;
}
