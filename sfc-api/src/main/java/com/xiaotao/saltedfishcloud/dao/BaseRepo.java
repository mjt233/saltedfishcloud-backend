package com.xiaotao.saltedfishcloud.dao;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

public interface BaseRepo<T extends AuditModel> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    @Transactional
    @Modifying
    @Query("DELETE FROM #{#entityName} t WHERE t.id IN :ids")
    int batchDelete(@Param("ids") Collection<Long> ids);
}
