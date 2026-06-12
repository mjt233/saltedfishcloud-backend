package com.sfc.dm.repo;

import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.model.po.InvalidDataRecord;
import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 失效数据记录Repository
 */
public interface InvalidDataRecordRepo extends BaseRepo<InvalidDataRecord> {
    /**
     * 按状态批量删除
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM InvalidDataRecord t WHERE t.status = :status")
    int deleteByStatus(@Param("status") InvalidDataStatus status);

    /**
     * 按状态查询ID列表
     */
    @Query("SELECT t.id FROM InvalidDataRecord t WHERE t.status = :status")
    List<Long> findIdsByStatus(@Param("status") InvalidDataStatus status);
}
