package com.sfc.cloud.data.service;

import com.sfc.cloud.data.identity.CurrentUserHolder;
import com.sfc.cloud.data.model.SfcAuditModel;
import com.sfc.cloud.data.repo.SfcBaseRepo;
import com.sfc.cloud.data.utils.SfcDBUtils;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.common.model.SfcPageInfo;
import com.sfc.common.model.param.SfcPageableRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.Transient;
import java.util.Collection;
import java.util.List;

/**
 * 增删改查服务的默认实现。
 *
 * <p>迁移自宿主的 {@code com.xiaotao.saltedfishcloud.service.CrudServiceImpl}，
 * 在保留全部方法语义的前提下完成解耦：</p>
 * <ul>
 *   <li>分页封装 {@code CommonPageInfo.of(...)} → {@link SfcPageInfo#of(org.springframework.data.domain.Page)}；</li>
 *   <li>分页参数 {@code PageableRequest} → {@link SfcPageableRequest}；</li>
 *   <li>权限校验 {@code UIDValidator.validateWithException(...)} → {@link SfcUIDValidator#validateWithException(long, boolean)}；</li>
 *   <li>当前用户 {@code SecureUtils.getCurrentUid()} → {@link CurrentUserHolder#getCurrentUid()}；</li>
 *   <li>批量插入 {@code DBUtils.batchInsert(...)} → {@link SfcDBUtils#batchInsert(JdbcTemplate, Iterable)}。</li>
 * </ul>
 *
 * <p><b>权限语义</b>：{@link #saveWithOwnerPermissions(SfcAuditModel)} 与
 * {@link #deleteWithOwnerPermissions(Long)} 默认禁止非管理员操作公共数据（uid = 0），
 * 子类如需放开可重写对应方法。</p>
 *
 * @param <T> 实体类型，必须是 {@link SfcAuditModel} 的子类
 * @param <R> 该实体的 Repository 类型，必须是 {@link SfcBaseRepo} 的子接口
 */
public abstract class SfcCrudServiceImpl<T extends SfcAuditModel, R extends SfcBaseRepo<T>> implements SfcCrudService<T> {

    /**
     * 实体对应的 JPA Repository，由 Spring 按泛型自动注入
     */
    @Autowired
    @Getter
    @Setter
    protected R repository;

    /**
     * JDBC 模板，用于 {@link #batchInsert(Iterable)} 的批量写入
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public T findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(T entity) {
        repository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveWithOwnerPermissions(T entity) {

        // 阻止篡改无权限的数据
        if (entity.getId() != null) {
            repository.findById(entity.getId()).ifPresent(existEntity -> {
                SfcUIDValidator.validateWithException(entity.getUid(), true);
            });
        }

        // 阻止创建不属于自己的数据
        if (entity.getUid() != null) {
            SfcUIDValidator.validateWithException(entity.getUid(), true);
        } else {
            entity.setUid(CurrentUserHolder.getCurrentUid());
        }

        save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteWithOwnerPermissions(Long id) {
        repository.findById(id).ifPresent(existEntity -> {
            SfcUIDValidator.validateWithException(id, true);
        });
        repository.deleteById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void batchSave(Collection<T> entityList) {
        repository.saveAll(entityList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<T> findAll() {
        return repository.findAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SfcPageInfo<T> findByUid(Long uid, SfcPageableRequest pageableRequest) {
        if (pageableRequest != null) {
            return SfcPageInfo.of(repository.findByUid(uid, PageRequest.of(pageableRequest.getPage(), pageableRequest.getSize())));
        } else {
            return SfcPageInfo.of(repository.findByUid(uid, null));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SfcPageInfo<T> findByUidWithOwnerPermissions(Long uid, SfcPageableRequest pageableRequest) {
        SfcUIDValidator.validateWithException(uid, false);
        return findByUid(uid, pageableRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<T> findByUid(Long uid) {
        return repository.findByUid(uid, null).getContent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int batchDelete(Collection<Long> ids) {
        return repository.batchDelete(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transient
    public void batchInsert(Iterable<T> entityList) {
        SfcDBUtils.batchInsert(jdbcTemplate, entityList);
    }
}
