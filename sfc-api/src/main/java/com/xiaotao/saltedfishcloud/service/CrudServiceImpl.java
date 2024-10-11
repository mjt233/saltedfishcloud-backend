package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import com.xiaotao.saltedfishcloud.model.CommonPageInfo;
import com.xiaotao.saltedfishcloud.model.param.PageableRequest;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import com.xiaotao.saltedfishcloud.service.CrudService;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.util.function.Tuple2;

import javax.persistence.Transient;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class CrudServiceImpl<T extends AuditModel, R extends BaseRepo<T>> implements CrudService<T> {
    @Autowired
    @Getter
    @Setter
    protected R repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public T findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public void save(T entity) {
        repository.save(entity);
    }

    @Override
    public void saveWithOwnerPermissions(T entity) {

        // 阻止篡改无权限的数据
        if (entity.getId() != null) {
            repository.findById(entity.getId()).ifPresent(existEntity -> {
                UIDValidator.validateWithException(entity.getUid(), true);
            });
        }

        // 阻止创建不属于自己的数据
        if (entity.getUid() != null) {
            UIDValidator.validateWithException(entity.getUid(), true);
        } else {
            entity.setUid(SecureUtils.getCurrentUid());
        }

        save(entity);
    }

    @Override
    public void deleteWithOwnerPermissions(Long id) {
        repository.findById(id).ifPresent(existEntity -> {
            UIDValidator.validateWithException(id, true);
        });
        repository.deleteById(id);
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

    @Override
    public CommonPageInfo<T> findByUid(Long uid, PageableRequest pageableRequest) {
        if (pageableRequest != null) {
            return CommonPageInfo.of(repository.findByUid(uid, PageRequest.of(pageableRequest.getPage(), pageableRequest.getSize())));
        } else {
            return CommonPageInfo.of(repository.findByUid(uid, null));
        }
    }

    @Override
    public CommonPageInfo<T> findByUidWithOwnerPermissions(Long uid, PageableRequest pageableRequest) {
        UIDValidator.validateWithException(uid, false);
        return findByUid(uid, pageableRequest);
    }

    @Override
    public List<T> findByUid(Long uid) {
        return repository.findByUid(uid, null).getContent();
    }

    @Override
    public int batchDelete(Collection<Long> ids) {
        return repository.batchDelete(ids);
    }

    @Override
    @Transient
    public void batchInsert(Iterable<T> entityList) {
        Objects.requireNonNull(entityList);
        Iterator<T> iterator = entityList.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("entityList is empty");
        }

        List<T> entityBatchList = new ArrayList<>();
        T sample = iterator.next();
        entityBatchList.add(sample);
        List<Tuple2<String, Method>> fields = ObjectUtils.getClassEntityFieldGetter(sample.getClass());
        String tableName = ObjectUtils.getEntityTableName(sample.getClass());

        // INSERT INTO xxxx (xx,xx,xx) VALUES (?,?,?),(?,?,?)
        StringBuilder sqlBuilder = new StringBuilder();
        Consumer<StringBuilder> buildInsert = sb ->
                sb.append("INSERT INTO `")
                .append(tableName).append("` (")
                .append(fields.stream().map(e -> "`" + e.getT1() + "`").collect(Collectors.joining(",")))
                .append(") VALUES ");
        BiConsumer<StringBuilder, List<T>> buildStmt = (sb, tList) -> {
            // 构造 (?,?,?),(?,?,?)
            String stmt = fields.stream().map(e -> "?").collect(Collectors.joining(","));
            sb.append(tList.stream().map(t -> "(" + stmt + ")").collect(Collectors.joining(",")));
        };
        BiConsumer<String, List<T>> doInsert = (sql, batchEntityList) -> {
            jdbcTemplate.update(sql, ps -> {
                int idx = 1;
                try {
                    for (T entity : batchEntityList) {
                        if (entity.getId() == null) {
                            entity.setId(IdUtil.getId());
                        }
                        if (entity.getCreateAt() == null) {
                            entity.setCreateAt(new Date());
                        }
                        if (entity.getUpdateAt() == null) {
                            entity.setUpdateAt(new Date());
                        }

                        for (Tuple2<String, Method> field : fields) {
                            ps.setObject(idx++, field.getT2().invoke(entity));
                        }
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        };
        int batchSize = 1000 / fields.size();

        while (iterator.hasNext()) {
            entityBatchList.add(iterator.next());
            if (entityBatchList.size() >= batchSize) {
                buildInsert.accept(sqlBuilder);
                buildStmt.accept(sqlBuilder, entityBatchList);
                doInsert.accept(sqlBuilder.toString(), entityBatchList);
                sqlBuilder.setLength(0);
                entityBatchList.clear();
            }
        }
        if (!entityBatchList.isEmpty()) {
            buildInsert.accept(sqlBuilder);
            buildStmt.accept(sqlBuilder, entityBatchList);
            doInsert.accept(sqlBuilder.toString(), entityBatchList);
            sqlBuilder.setLength(0);
            entityBatchList.clear();
        }
    }
}
