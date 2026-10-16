package com.sfc.cloud.data.repo;

import com.sfc.cloud.data.model.SfcAuditModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 带数据持有人（uid）语义的 JPA Repository 基接口。
 *
 * <p>迁移自宿主的 {@code com.xiaotao.saltedfishcloud.dao.BaseRepo}，
 * 仅把泛型上界由 {@code AuditModel} 换成 {@link SfcAuditModel}，
 * 三个查询方法的 JPQL、注解与签名逐字保留。</p>
 *
 * <p>所有查询均通过 SpEL 表达式 {@code #{#entityName}} 引用具体实体名，
 * 因此子接口无需重复声明即可获得统一的按 uid 查询与批量删除能力。</p>
 *
 * @param <T> 实体类型，必须是 {@link SfcAuditModel} 的子类
 */
public interface SfcBaseRepo<T extends SfcAuditModel> extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

    /**
     * 按id批量删除数据。
     *
     * @param ids 待删除的数据id集合
     * @return 实际删除的记录数
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM #{#entityName} t WHERE t.id IN :ids")
    int batchDelete(@Param("ids") Collection<Long> ids);

    /**
     * 按数据所属人分页查询，按id倒序排列。
     *
     * @param uid      数据所属人uid
     * @param pageable 分页参数，为 {@code null} 时表示不分页
     * @return 分页查询结果
     */
    @Query("SELECT t FROM #{#entityName} t WHERE t.uid = :uid ORDER BY t.id DESC")
    Page<T> findByUid(@Param("uid") Long uid, Pageable pageable);

    /**
     * 按id集合批量查询数据。
     *
     * @param ids 数据id集合
     * @return 查询到的数据列表
     */
    @Query("SELECT t FROM #{#entityName} t WHERE t.id IN :ids")
    List<T> findByIds(@Param("ids") Collection<Long> ids);
}
