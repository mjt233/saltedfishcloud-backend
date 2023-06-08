package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BaseRepo<T extends AuditModel> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

}
