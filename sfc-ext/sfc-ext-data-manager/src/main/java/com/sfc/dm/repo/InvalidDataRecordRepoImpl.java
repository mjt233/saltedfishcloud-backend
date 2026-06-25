package com.sfc.dm.repo;

import com.sfc.dm.model.po.InvalidDataRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.jpa.AvailableHints;
import org.springframework.data.jpa.domain.Specification;

import java.util.stream.Stream;

/**
 * {@link InvalidDataRecordRepoCustom} 实现，使用 {@link EntityManager} 流式查询以减少内存占用。
 */
public class InvalidDataRecordRepoImpl implements InvalidDataRecordRepoCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Stream<InvalidDataRecord> streamAll(Specification<InvalidDataRecord> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<InvalidDataRecord> query = cb.createQuery(InvalidDataRecord.class);
        Root<InvalidDataRecord> root = query.from(InvalidDataRecord.class);

        if (spec != null) {
            Predicate predicate = spec.toPredicate(root, query, cb);
            if (predicate != null) {
                query.where(predicate);
            }
        }

        TypedQuery<InvalidDataRecord> typedQuery = entityManager.createQuery(query);
        typedQuery.setHint(AvailableHints.HINT_FETCH_SIZE, 100);
        return typedQuery.getResultStream();
    }
}
