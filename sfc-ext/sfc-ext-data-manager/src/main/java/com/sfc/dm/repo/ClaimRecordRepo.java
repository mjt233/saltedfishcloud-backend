package com.sfc.dm.repo;

import com.sfc.dm.model.po.ClaimRecord;
import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 认领记录Repository
 */
public interface ClaimRecordRepo extends BaseRepo<ClaimRecord> {
    /**
     * 按失效数据ID查询所有认领记录
     */
    List<ClaimRecord> findByInvalidDataId(Long invalidDataId);

    /**
     * 按失效数据ID查询认领数量
     */
    @Query("SELECT COUNT(t) FROM ClaimRecord t WHERE t.invalidDataId = :invalidDataId")
    long countByInvalidDataId(@Param("invalidDataId") Long invalidDataId);

    /**
     * 按用户ID查询（用户查看自己的认领记录）
     */
    Page<ClaimRecord> findByUid(Long uid, Pageable pageable);
}
