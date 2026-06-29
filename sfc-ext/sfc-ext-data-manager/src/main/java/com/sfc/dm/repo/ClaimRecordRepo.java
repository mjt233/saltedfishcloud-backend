package com.sfc.dm.repo;

import com.sfc.dm.model.po.ClaimRecord;
import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 按失效数据ID列表查询所有未撤回的认领记录
     */
    @Query("SELECT t FROM ClaimRecord t WHERE t.invalidDataId IN :ids AND (t.isRevoked = false OR t.isRevoked IS NULL)")
    List<ClaimRecord> findByInvalidDataIdInAndIsRevokedFalse(@Param("ids") List<Long> ids);

    /**
     * 批量标记认领记录为已撤回
     */
    @Transactional
    @Modifying
    @Query("UPDATE ClaimRecord t SET t.isRevoked = true WHERE t.id IN :ids")
    int batchMarkRevoked(@Param("ids") List<Long> ids);
}
