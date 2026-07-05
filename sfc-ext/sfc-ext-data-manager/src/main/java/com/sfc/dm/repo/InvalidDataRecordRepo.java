package com.sfc.dm.repo;

import com.sfc.dm.enums.InvalidDataStatus;
import com.sfc.dm.enums.ProcessMethod;
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
public interface InvalidDataRecordRepo extends BaseRepo<InvalidDataRecord>, InvalidDataRecordRepoCustom {
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

    /**
     * 批量更新状态
     */
    @Transactional
    @Modifying
    @Query("UPDATE InvalidDataRecord t SET t.status = :status WHERE t.id IN :ids")
    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") InvalidDataStatus status);

    /**
     * 批量更新处理结果（同时设置状态与处理方式）。
     *
     * @param ids          待更新的记录ID列表
     * @param status       目标状态
     * @param processMethod 处理方式
     * @return 实际更新的记录数
     */
    @Transactional
    @Modifying
    @Query("UPDATE InvalidDataRecord t SET t.status = :status, t.processMethod = :processMethod WHERE t.id IN :ids")
    int updateProcessResultByIds(@Param("ids") List<Long> ids,
                                 @Param("status") InvalidDataStatus status,
                                 @Param("processMethod") ProcessMethod processMethod);
}
